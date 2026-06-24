package com.javaee.donation.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.javaee.donation.analytics.dto.AnalyticsRebuildResponse;
import com.javaee.donation.analytics.entity.RewardEvent;
import com.javaee.donation.analytics.entity.RewardHourlyStat;
import com.javaee.donation.analytics.entity.StreamerViewerSummary;
import com.javaee.donation.analytics.entity.ViewerProfile;
import com.javaee.donation.analytics.exception.AnalyticsBusinessException;
import com.javaee.donation.analytics.mapper.RewardEventMapper;
import com.javaee.donation.analytics.mapper.RewardHourlyStatMapper;
import com.javaee.donation.analytics.mapper.StreamerViewerSummaryMapper;
import com.javaee.donation.analytics.mapper.ViewerProfileMapper;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.HourlyStatResponse;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final String SETTLED = "SETTLED";
    private static final int MAX_TOP_VIEWERS_LIMIT = 10;

    private final ViewerProfileMapper viewerProfileMapper;
    private final RewardHourlyStatMapper rewardHourlyStatMapper;
    private final StreamerViewerSummaryMapper streamerViewerSummaryMapper;
    private final RewardEventMapper rewardEventMapper;

    public AnalyticsService(ViewerProfileMapper viewerProfileMapper,
                            RewardHourlyStatMapper rewardHourlyStatMapper,
                            StreamerViewerSummaryMapper streamerViewerSummaryMapper,
                            RewardEventMapper rewardEventMapper) {
        this.viewerProfileMapper = viewerProfileMapper;
        this.rewardHourlyStatMapper = rewardHourlyStatMapper;
        this.streamerViewerSummaryMapper = streamerViewerSummaryMapper;
        this.rewardEventMapper = rewardEventMapper;
    }

    public ViewerProfileResponse getProfile(String viewerId) {
        if (viewerId == null || viewerId.isBlank()) {
            throw new AnalyticsBusinessException("INVALID_VIEWER_ID", "观众ID不能为空");
        }
        log.info("[{}] query viewer profile, viewerId={}", TraceContext.getTraceId(), viewerId);
        ViewerProfile profile = viewerProfileMapper.selectOne(
                new LambdaQueryWrapper<ViewerProfile>().eq(ViewerProfile::getViewerId, viewerId));
        if (profile == null) {
            return new ViewerProfileResponse(viewerId, viewerId, "PENDING", BigDecimal.ZERO);
        }
        return new ViewerProfileResponse(profile.getViewerId(), profile.getViewerName(),
                profile.getProfileTag(), safeAmount(profile.getProfileScore()));
    }

    public List<TopViewerResponse> getTopViewers(String streamerId, Integer limit) {
        if (streamerId == null || streamerId.isBlank()) {
            throw new AnalyticsBusinessException("INVALID_STREAMER_ID", "主播ID不能为空");
        }
        int pageSize = limit == null || limit <= 0 ? MAX_TOP_VIEWERS_LIMIT : Math.min(limit, MAX_TOP_VIEWERS_LIMIT);
        log.info("[{}] query top viewers, streamerId={}, limit={}", TraceContext.getTraceId(), streamerId, pageSize);
        Page<StreamerViewerSummary> page = Page.of(1, pageSize, false);
        LambdaQueryWrapper<StreamerViewerSummary> wrapper = new LambdaQueryWrapper<StreamerViewerSummary>()
                .eq(StreamerViewerSummary::getStreamerId, streamerId)
                .orderByDesc(StreamerViewerSummary::getTotalRewardAmount)
                .orderByDesc(StreamerViewerSummary::getRewardCount)
                .orderByAsc(StreamerViewerSummary::getViewerId);
        return streamerViewerSummaryMapper.selectPage(page, wrapper).getRecords().stream()
                .map(summary -> new TopViewerResponse(summary.getViewerId(), summary.getViewerName(),
                        safeAmount(summary.getTotalRewardAmount()), safeCount(summary.getRewardCount())))
                .toList();
    }

    public List<HourlyStatResponse> getHourlyStats(String startHour, String endHour, String gender, String streamerId) {
        LocalDateTime start = parseRequiredHour(startHour, "startHour");
        LocalDateTime end = parseRequiredHour(endHour, "endHour");
        if (end.isBefore(start)) {
            throw new AnalyticsBusinessException("INVALID_TIME_RANGE", "结束时间不能早于开始时间");
        }
        long hourSpan = Duration.between(start, end).toHours();
        if (hourSpan > 168) {
            throw new AnalyticsBusinessException("TIME_RANGE_TOO_LARGE", "查询时间范围不能超过168小时");
        }
        log.info("[{}] query hourly stats, startHour={}, endHour={}, gender={}, streamerId={}",
                TraceContext.getTraceId(), start, end, gender, streamerId);

        LambdaQueryWrapper<RewardHourlyStat> wrapper = new LambdaQueryWrapper<RewardHourlyStat>()
                .ge(RewardHourlyStat::getStatHour, start)
                .le(RewardHourlyStat::getStatHour, end)
                .eq(gender != null && !gender.isBlank(), RewardHourlyStat::getViewerGender, gender)
                .eq(streamerId != null && !streamerId.isBlank(), RewardHourlyStat::getStreamerId, streamerId)
                .orderByAsc(RewardHourlyStat::getStatHour)
                .orderByAsc(RewardHourlyStat::getStreamerId)
                .orderByAsc(RewardHourlyStat::getViewerGender);

        List<RewardHourlyStat> stats = rewardHourlyStatMapper.selectList(wrapper);
        return fillMissingHours(start, end, gender, streamerId, stats);
    }

    @Transactional(rollbackFor = Exception.class)
    public AnalyticsRebuildResponse rebuild() {
        log.info("[{}] analytics rebuild start", TraceContext.getTraceId());
        List<RewardEvent> events = rewardEventMapper.selectList(
                new LambdaQueryWrapper<RewardEvent>()
                        .eq(RewardEvent::getSettleStatus, SETTLED)
                        .orderByAsc(RewardEvent::getRewardTime)
                        .orderByAsc(RewardEvent::getId));

        rewardHourlyStatMapper.delete(null);
        streamerViewerSummaryMapper.delete(null);
        viewerProfileMapper.delete(null);

        Map<HourlyStatKey, RewardHourlyStat> hourlyStats = buildHourlyStats(events);
        Map<StreamerViewerKey, StreamerViewerSummary> streamerViewerSummaries = buildStreamerViewerSummaries(events);
        List<ViewerProfile> viewerProfiles = buildViewerProfiles(streamerViewerSummaries.values());

        hourlyStats.values().forEach(rewardHourlyStatMapper::insert);
        streamerViewerSummaries.values().forEach(streamerViewerSummaryMapper::insert);
        viewerProfiles.forEach(viewerProfileMapper::insert);

        AnalyticsRebuildResponse response = AnalyticsRebuildResponse.builder()
                .viewerProfiles(viewerProfiles.size())
                .hourlyStats(hourlyStats.size())
                .streamerViewerSummaries(streamerViewerSummaries.size())
                .rebuiltAt(LocalDateTime.now())
                .build();
        log.info("[{}] analytics rebuild done, viewerProfiles={}, hourlyStats={}, streamerViewerSummaries={}",
                TraceContext.getTraceId(), response.getViewerProfiles(), response.getHourlyStats(),
                response.getStreamerViewerSummaries());
        return response;
    }

    private List<HourlyStatResponse> fillMissingHours(LocalDateTime start,
                                                      LocalDateTime end,
                                                      String gender,
                                                      String streamerId,
                                                      List<RewardHourlyStat> stats) {
        if ((gender == null || gender.isBlank()) || (streamerId == null || streamerId.isBlank())) {
            return stats.stream()
                    .map(stat -> new HourlyStatResponse(stat.getStatHour().toString(), stat.getStreamerId(),
                            stat.getViewerGender(), safeAmount(stat.getRewardAmount()), safeCount(stat.getRewardCount())))
                    .toList();
        }

        Map<LocalDateTime, RewardHourlyStat> statMap = new LinkedHashMap<>();
        for (RewardHourlyStat stat : stats) {
            statMap.put(stat.getStatHour(), stat);
        }

        List<HourlyStatResponse> responses = new ArrayList<>();
        LocalDateTime current = start;
        while (!current.isAfter(end)) {
            RewardHourlyStat stat = statMap.get(current);
            if (stat == null) {
                responses.add(new HourlyStatResponse(current.toString(), streamerId, gender, BigDecimal.ZERO, 0L));
            } else {
                responses.add(new HourlyStatResponse(stat.getStatHour().toString(), stat.getStreamerId(),
                        stat.getViewerGender(), safeAmount(stat.getRewardAmount()), safeCount(stat.getRewardCount())));
            }
            current = current.plusHours(1);
        }
        return responses;
    }

    private Map<HourlyStatKey, RewardHourlyStat> buildHourlyStats(List<RewardEvent> events) {
        Map<HourlyStatKey, RewardHourlyStat> hourlyStats = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (RewardEvent event : events) {
            LocalDateTime statHour = event.getRewardTime().truncatedTo(ChronoUnit.HOURS);
            HourlyStatKey key = new HourlyStatKey(statHour, event.getStreamerId(), event.getViewerGender());
            RewardHourlyStat stat = hourlyStats.computeIfAbsent(key, ignored -> {
                RewardHourlyStat created = new RewardHourlyStat();
                created.setStatHour(statHour);
                created.setStreamerId(event.getStreamerId());
                created.setViewerGender(event.getViewerGender());
                created.setRewardAmount(BigDecimal.ZERO);
                created.setRewardCount(0L);
                created.setCreatedAt(now);
                created.setUpdatedAt(now);
                return created;
            });
            stat.setRewardAmount(safeAmount(stat.getRewardAmount()).add(safeAmount(event.getRewardAmount())));
            stat.setRewardCount(safeCount(stat.getRewardCount()) + 1);
        }
        return hourlyStats;
    }

    private Map<StreamerViewerKey, StreamerViewerSummary> buildStreamerViewerSummaries(List<RewardEvent> events) {
        Map<StreamerViewerKey, StreamerViewerSummary> summaries = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (RewardEvent event : events) {
            StreamerViewerKey key = new StreamerViewerKey(event.getStreamerId(), event.getViewerId());
            StreamerViewerSummary summary = summaries.computeIfAbsent(key, ignored -> {
                StreamerViewerSummary created = new StreamerViewerSummary();
                created.setStreamerId(event.getStreamerId());
                created.setViewerId(event.getViewerId());
                created.setViewerName(event.getViewerName());
                created.setTotalRewardAmount(BigDecimal.ZERO);
                created.setRewardCount(0L);
                created.setUpdatedAt(now);
                return created;
            });
            summary.setViewerName(event.getViewerName());
            summary.setTotalRewardAmount(safeAmount(summary.getTotalRewardAmount()).add(safeAmount(event.getRewardAmount())));
            summary.setRewardCount(safeCount(summary.getRewardCount()) + 1);
        }
        return summaries;
    }

    private List<ViewerProfile> buildViewerProfiles(Iterable<StreamerViewerSummary> summaries) {
        Map<String, ViewerTotal> viewerTotals = new LinkedHashMap<>();
        for (StreamerViewerSummary summary : summaries) {
            ViewerTotal total = viewerTotals.computeIfAbsent(summary.getViewerId(),
                    viewerId -> new ViewerTotal(viewerId, summary.getViewerName(), BigDecimal.ZERO));
            total.totalRewardAmount = total.totalRewardAmount.add(safeAmount(summary.getTotalRewardAmount()));
            total.viewerName = summary.getViewerName();
        }

        List<ViewerTotal> ranking = new ArrayList<>(viewerTotals.values());
        ranking.sort(Comparator.comparing((ViewerTotal item) -> item.totalRewardAmount).reversed()
                .thenComparing(item -> item.viewerId));

        List<ViewerProfile> profiles = new ArrayList<>();
        int totalSize = ranking.size();
        LocalDateTime now = LocalDateTime.now();
        int highCount = totalSize == 0 ? 0 : Math.max(1, (int) Math.ceil(totalSize * 0.2D));
        int lowCount = totalSize == 0 ? 0 : Math.max(1, (int) Math.ceil(totalSize * 0.2D));
        int lowStartIndex = Math.max(highCount, totalSize - lowCount);
        for (int index = 0; index < ranking.size(); index++) {
            ViewerTotal total = ranking.get(index);
            ViewerProfile profile = new ViewerProfile();
            profile.setViewerId(total.viewerId);
            profile.setViewerName(total.viewerName);
            profile.setProfileTag(profileTag(index, highCount, lowStartIndex));
            profile.setProfileScore(total.totalRewardAmount);
            profile.setUpdatedAt(now);
            profiles.add(profile);
        }
        return profiles;
    }

    private String profileTag(int index, int highCount, int lowStartIndex) {
        if (index < highCount) {
            return "HIGH";
        }
        if (index >= lowStartIndex) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private LocalDateTime parseRequiredHour(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AnalyticsBusinessException("INVALID_" + fieldName.toUpperCase(), fieldName + "不能为空");
        }
        // 支持两种格式：ISO时间(2026-06-18T20:00) 或 纯小时数字(18)
        try {
            // 先尝试解析为纯小时数字（0-23）
            int hour = Integer.parseInt(value.trim());
            if (hour >= 0 && hour <= 23) {
                return LocalDate.now().atTime(hour, 0);
            }
            throw new AnalyticsBusinessException("INVALID_" + fieldName.toUpperCase(), fieldName + "必须在0-23之间");
        } catch (NumberFormatException ignored) {
            // 不是数字，尝试 ISO-8601 时间格式
        }
        try {
            return LocalDateTime.parse(value).truncatedTo(ChronoUnit.HOURS);
        } catch (Exception exception) {
            throw new AnalyticsBusinessException("INVALID_" + fieldName.toUpperCase(), fieldName + "格式错误，要求小时数字(0-23)或ISO-8601时间格式");
        }
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private Long safeCount(Long count) {
        return count == null ? 0L : count;
    }

    private record HourlyStatKey(LocalDateTime statHour, String streamerId, String viewerGender) {
    }

    private record StreamerViewerKey(String streamerId, String viewerId) {
    }

    private static final class ViewerTotal {
        private final String viewerId;
        private String viewerName;
        private BigDecimal totalRewardAmount;

        private ViewerTotal(String viewerId, String viewerName, BigDecimal totalRewardAmount) {
            this.viewerId = Objects.requireNonNull(viewerId);
            this.viewerName = viewerName;
            this.totalRewardAmount = totalRewardAmount;
        }
    }
}
