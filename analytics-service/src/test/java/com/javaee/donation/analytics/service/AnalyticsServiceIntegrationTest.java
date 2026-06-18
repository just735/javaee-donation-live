package com.javaee.donation.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.cloud.consul.enabled=false")
class AnalyticsServiceIntegrationTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private RewardEventMapper rewardEventMapper;

    @Autowired
    private ViewerProfileMapper viewerProfileMapper;

    @Autowired
    private RewardHourlyStatMapper rewardHourlyStatMapper;

    @Autowired
    private StreamerViewerSummaryMapper streamerViewerSummaryMapper;

    @BeforeEach
    void setUp() {
        TraceContext.setTraceId("analytics-test");
        seedEvents();
    }

    @AfterEach
    void tearDown() {
        rewardHourlyStatMapper.delete(null);
        streamerViewerSummaryMapper.delete(null);
        viewerProfileMapper.delete(null);
        rewardEventMapper.delete(null);
        TraceContext.clear();
    }

    @Test
    @DisplayName("重建后可生成画像、小时统计和主播观众汇总")
    void rebuild_GeneratesDerivedTables() {
        AnalyticsRebuildResponse response = analyticsService.rebuild();

        assertEquals(3, response.getViewerProfiles());
        assertEquals(5, response.getHourlyStats());
        assertEquals(4, response.getStreamerViewerSummaries());
    }

    @Test
    @DisplayName("画像查询按前20%/20%-80%/后20%输出高中低消费标签")
    void getProfile_ReturnsExpectedTag() {
        analyticsService.rebuild();

        ViewerProfileResponse high = analyticsService.getProfile("viewer-1");
        ViewerProfileResponse medium = analyticsService.getProfile("viewer-2");
        ViewerProfileResponse low = analyticsService.getProfile("viewer-3");

        assertEquals("HIGH", high.getProfileTag());
        assertEquals("MEDIUM", medium.getProfileTag());
        assertEquals("LOW", low.getProfileTag());
    }

    @Test
    @DisplayName("画像不存在时返回PENDING")
    void getProfile_PendingWhenMissing() {
        ViewerProfileResponse pending = analyticsService.getProfile("unknown-viewer");
        assertEquals("PENDING", pending.getProfileTag());
        assertEquals(BigDecimal.ZERO, pending.getProfileScore());
    }

    @Test
    @DisplayName("Top10 查询按总打赏金额倒序返回")
    void getTopViewers_ReturnsSortedRecords() {
        analyticsService.rebuild();

        List<TopViewerResponse> topViewers = analyticsService.getTopViewers("streamer-1", 10);
        assertEquals(2, topViewers.size());
        assertEquals("viewer-1", topViewers.get(0).getViewerId());
        assertEquals(new BigDecimal("150.00"), topViewers.get(0).getTotalRewardAmount());
        assertEquals("viewer-2", topViewers.get(1).getViewerId());
    }

    @Test
    @DisplayName("小时统计在固定主播和性别查询时补齐空小时")
    void getHourlyStats_FillsMissingHours() {
        analyticsService.rebuild();

        List<HourlyStatResponse> stats = analyticsService.getHourlyStats(
                "2026-06-18T18:00:00",
                "2026-06-18T22:00:00",
                "MALE",
                "streamer-1");

        assertEquals(5, stats.size());
        assertEquals("2026-06-18T18:00", stats.get(0).getStatHour());
        assertEquals(new BigDecimal("100.00"), stats.get(0).getRewardAmount());
        assertEquals(BigDecimal.ZERO, stats.get(1).getRewardAmount());
        assertEquals(new BigDecimal("50.00"), stats.get(2).getRewardAmount());
        assertEquals(BigDecimal.ZERO, stats.get(4).getRewardAmount());
    }

    @Test
    @DisplayName("小时统计参数非法时抛业务异常")
    void getHourlyStats_RejectsInvalidRange() {
        assertThrows(AnalyticsBusinessException.class,
                () -> analyticsService.getHourlyStats("2026-06-18T22:00:00", "2026-06-18T18:00:00", "MALE", "streamer-1"));
    }

    @Test
    @DisplayName("画像查询参数为空时抛业务异常")
    void getProfile_RejectsBlankViewerId() {
        assertThrows(AnalyticsBusinessException.class, () -> analyticsService.getProfile(" "));
    }

    private void seedEvents() {
        rewardHourlyStatMapper.delete(null);
        streamerViewerSummaryMapper.delete(null);
        viewerProfileMapper.delete(null);
        rewardEventMapper.delete(null);

        insertEvent(1L, "reward-1", "viewer-1", "观众一", "MALE", "streamer-1", "主播一",
                "100.00", "2026-06-18T18:10:00", "SETTLED");
        insertEvent(2L, "reward-2", "viewer-1", "观众一", "MALE", "streamer-1", "主播一",
                "50.00", "2026-06-18T20:20:00", "SETTLED");
        insertEvent(3L, "reward-3", "viewer-2", "观众二", "MALE", "streamer-1", "主播一",
                "80.00", "2026-06-18T21:30:00", "SETTLED");
        insertEvent(4L, "reward-4", "viewer-2", "观众二", "FEMALE", "streamer-2", "主播二",
                "20.00", "2026-06-18T19:15:00", "SETTLED");
        insertEvent(5L, "reward-5", "viewer-3", "观众三", "FEMALE", "streamer-2", "主播二",
                "10.00", "2026-06-18T22:40:00", "SETTLED");
        insertEvent(6L, "reward-6", "viewer-4", "观众四", "MALE", "streamer-1", "主播一",
                "999.00", "2026-06-18T18:40:00", "PENDING");
    }

    private void insertEvent(Long id,
                             String rewardNo,
                             String viewerId,
                             String viewerName,
                             String viewerGender,
                             String streamerId,
                             String streamerName,
                             String rewardAmount,
                             String rewardTime,
                             String settleStatus) {
        RewardEvent event = new RewardEvent();
        event.setId(id);
        event.setRewardNo(rewardNo);
        event.setTraceId("trace-" + rewardNo);
        event.setViewerId(viewerId);
        event.setViewerName(viewerName);
        event.setViewerGender(viewerGender);
        event.setStreamerId(streamerId);
        event.setStreamerName(streamerName);
        event.setRewardAmount(new BigDecimal(rewardAmount));
        event.setRewardTime(LocalDateTime.parse(rewardTime));
        event.setSettleStatus(settleStatus);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        rewardEventMapper.insert(event);
    }
}
