package com.javaee.donation.viewer.service;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import com.javaee.donation.viewer.client.AnalyticsClient;
import com.javaee.donation.viewer.constant.ViewerConstants;
import com.javaee.donation.viewer.dto.TopViewersFetchResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsGatewayTest {

    @Mock
    private AnalyticsClient analyticsClient;

    private AnalyticsGateway analyticsGateway;

    @BeforeEach
    void setUp() {
        analyticsGateway = new AnalyticsGateway(analyticsClient, new ViewerFallbackService());
    }

    @Test
    void profileShouldDegradeOnTimeout() {
        when(analyticsClient.profile("v-slow")).thenAnswer(invocation -> {
            Thread.sleep(3000);
            return ApiResponse.success("trace", new ViewerProfileResponse("v-slow", "n", "HIGH"));
        });

        ViewerProfileResponse profile = analyticsGateway.getProfile("v-slow");

        assertEquals(ViewerConstants.PROFILE_TAG_PENDING, profile.getProfileTag());
    }

    @Test
    void profileShouldDegradeOnDownstreamException() {
        when(analyticsClient.profile("v-err")).thenThrow(new RuntimeException("analytics down"));

        ViewerProfileResponse profile = analyticsGateway.getProfile("v-err");

        assertEquals(ViewerConstants.PROFILE_TAG_PENDING, profile.getProfileTag());
    }

    @Test
    void topViewersShouldReturnNormalData() {
        TopViewerResponse top = new TopViewerResponse("v-1", "观众1", new BigDecimal("100.00"));
        when(analyticsClient.topViewers("s-1", 10))
                .thenReturn(ApiResponse.success("trace", List.of(top)));

        TopViewersFetchResult result = analyticsGateway.getTopViewers("s-1", 10);

        assertEquals(false, result.isDegraded());
        assertEquals(1, result.getViewers().size());
    }
}
