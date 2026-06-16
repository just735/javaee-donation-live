package com.javaee.donation.analytics.service;

import com.javaee.donation.common.model.HourlyStatResponse;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final Map<String, BigDecimal> viewerTotalAmounts = new ConcurrentHashMap<>();
    private final Map<String, String> viewerNames = new ConcurrentHashMap<>();
    private final Map<String, Map<String, TopViewerResponse>> streamerViewerTotals = new ConcurrentHashMap<>();
    private final Map<String, HourlyStatResponse> hourlyStats = new ConcurrentHashMap<>();

    public String collect(RewardRequest request) {
        BigDecimal rewardAmount = request.getRewardAmount() == null ? BigDecimal.ZERO : request.getRewardAmount();
        viewerTotalAmounts.merge(request.getViewerId(), rewardAmount, BigDecimal::add);
        viewerNames.put(request.getViewerId(), request.getViewerName());

        streamerViewerTotals.computeIfAbsent(request.getStreamerId(), key -> new ConcurrentHashMap<>())
                .compute(request.getViewerId(), (viewerId, current) -> {
                    if (current == null) {
                        return new TopViewerResponse(viewerId, request.getViewerName(), rewardAmount);
                    }
                    current.setTotalRewardAmount(current.getTotalRewardAmount().add(rewardAmount));
                    return current;
                });

        LocalDateTime statHour = parseTime(request.getRewardTime()).truncatedTo(ChronoUnit.HOURS);
        String hourlyKey = statHour + "|" + request.getStreamerId() + "|" + request.getViewerGender();
        hourlyStats.compute(hourlyKey, (key, current) -> {
            if (current == null) {
                return new HourlyStatResponse(statHour.toString(), request.getStreamerId(), request.getViewerGender(),
                        rewardAmount, 1L);
            }
            current.setRewardAmount(current.getRewardAmount().add(rewardAmount));
            current.setRewardCount(current.getRewardCount() + 1);
            return current;
        });
        return "analytics updated";
    }

    public ViewerProfileResponse getProfile(String viewerId) {
        List<Map.Entry<String, BigDecimal>> ranking = viewerTotalAmounts.entrySet().stream()
                .sorted((left, right) -> right.getValue().compareTo(left.getValue()))
                .toList();
        int index = 0;
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).getKey().equals(viewerId)) {
                index = i;
                break;
            }
        }
        String profileTag = profileTag(index, ranking.size());
        return new ViewerProfileResponse(viewerId, viewerNames.getOrDefault(viewerId, viewerId), profileTag);
    }

    public List<TopViewerResponse> getTopViewers(String streamerId, Integer limit) {
        return streamerViewerTotals.getOrDefault(streamerId, Map.of()).values().stream()
                .sorted(Comparator.comparing(TopViewerResponse::getTotalRewardAmount).reversed())
                .limit(limit == null ? 10 : limit)
                .collect(Collectors.toList());
    }

    public List<HourlyStatResponse> getHourlyStats(String startHour, String endHour, String gender, String streamerId) {
        LocalDateTime start = parseTime(startHour);
        LocalDateTime end = parseTime(endHour);
        List<HourlyStatResponse> result = new ArrayList<>();
        for (HourlyStatResponse stat : hourlyStats.values()) {
            LocalDateTime currentHour = parseTime(stat.getStatHour());
            boolean inRange = (!currentHour.isBefore(start)) && (!currentHour.isAfter(end));
            boolean sameGender = gender == null || gender.isBlank() || gender.equalsIgnoreCase(stat.getViewerGender());
            boolean sameStreamer = streamerId == null || streamerId.isBlank() || streamerId.equals(stat.getStreamerId());
            if (inRange && sameGender && sameStreamer) {
                result.add(stat);
            }
        }
        result.sort(Comparator.comparing(HourlyStatResponse::getStatHour));
        return result;
    }

    public String rebuild() {
        return "rebuild skipped in local mode";
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

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        return LocalDateTime.parse(value);
    }
}
