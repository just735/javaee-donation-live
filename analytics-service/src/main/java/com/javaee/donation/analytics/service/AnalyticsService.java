package com.javaee.donation.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.javaee.donation.analytics.dto.AnalyticsRebuildResponse;
import com.javaee.donation.analytics.entity.RewardEvent;
import com.javaee.donation.analytics.entity.RewardHourlyStat;
import com.javaee.donation.analytics.entity.StreamerViewerSummary;
import com.javaee.donation.analytics.entity.ViewerProfile;
import com.javaee.donation.analytics.mapper.RewardEventMapper;
import com.javaee.donation.analytics.mapper.RewardHourlyStatMapper;
import com.javaee.donation.analytics.mapper.StreamerViewerSummaryMapper;
import com.javaee.donation.analytics.mapper.ViewerProfileMapper;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.HourlyStatResponse;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import java.math.BigDecimal;
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
        int pageSize = limit == null || limit <= 0 ? 10 : Math.min(limit, 100);
        log.info("[{}] query top viewers, streamerId={}, limit={}", TraceContext.getTraceId(), streamerId, pageSize);
        Page<StreamerViewerSummary> page = Page.of(1, pageSize, false);
        LambdaQueryWrapper<StreamerViewerSummary> wrapper = new LambdaQueryWrapper<StreamerViewerSummary>()
                .eq(StreamerViewerSummary::getStreamerId, streamerId)
                .orderByDesc(StreamerViewerSummary::getTotalRewardAmount)
                .orderByDesc(StreamerViewerSummary::getRewardCount);
        return streamerViewerSummaryMapper.selectPage(page, wrapper).getRecords().stream()
                .map(summary -> new TopViewerResponse(summary.getViewerId(), summary.getViewerName(),
                        safeAmount(summary.getTotalRewardAmount()), safeCount(summary.getRewardCount())))
                .toList();
    }

    public List<HourlyStatResponse> getHourlyStats(String startHour, String endHour, String gender, String streamerId) {
        LocalDateTime start = parseTime(startHour, true);
        LocalDateTime end = parseTime(endHour, false);
        log.info("[{}] query hourly stats, startHour={}, endHour={}, gender={}, streamerId={}",
                TraceContext.getTraceId(), start, end, gender, streamerId);

        LambdaQueryWrapper<RewardHourlyStat> wrapper = new LambdaQueryWrapper<RewardHourlyStat>()
                .ge(start != null, RewardHourlyStat::getStatHour, start)
                .le(end != null, RewardHourlyStat::getStatHour, end)
                .eq(gender != null && !gender.isBlank(), RewardHourlyStat::getViewerGender, gender)
                .eq(streamerId != null && !streamerId.isBlank(), RewardHourlyStat::getStreamerId, streamerId)
                .orderByAsc(RewardHourlyStat::getStatHour)
                .orderByAsc(RewardHourlyStat::getStreamerId)
                .orderByAsc(RewardHourlyStat::getViewerGender);

        return rewardHourlyStatMapper.selectList(wrapper).stream()
                .map(stat -> new HourlyStatResponse(stat.getStatHour().toString(), stat.getStreamerId(),
                        stat.getViewerGender(), safeAmount(stat.getRewardAmount()), safeCount(stat.getRewardCount())))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AnalyticsRebuildResponse rebuild() {
        log.info("[{}] analytics rebuild start", TraceContext.getTraceId());
        List<RewardEvent> events = rewardEventMapper.selectList(
                new LambdaQueryWrapper<RewardEvent>()
                        .eq(RewardEvent::getSettleStatus, SETTLED)
                        .orderByAsc(RewardEvent::getRewardTime));

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
        for (int index = 0; index < ranking.size(); index++) {
            ViewerTotal total = ranking.get(index);
            ViewerProfile profile = new ViewerProfile();
            profile.setViewerId(total.viewerId);
            profile.setViewerName(total.viewerName);
            profile.setProfileTag(profileTag(index, totalSize));
            profile.setProfileScore(total.totalRewardAmount);
            profile.setUpdatedAt(now);
            profiles.add(profile);
        }
        return profiles;
    }

    private String profileTag(int index, int size) {
        if (size == 0) {
            return "LOW";
        }
        double percentile = (index + 1D) / size;
        if (percentile <= 0.2D) {
            return "HIGH";
        }
        if (percentile <= 0.8D) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private LocalDateTime parseTime(String value, boolean start) {
        if (value == null || value.isBlank()) {
            return null;
        }
        LocalDateTime time = LocalDateTime.parse(value);
        return start ? time.truncatedTo(ChronoUnit.HOURS) : time.truncatedTo(ChronoUnit.HOURS);
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
