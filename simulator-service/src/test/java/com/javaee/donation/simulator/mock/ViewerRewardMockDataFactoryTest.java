package com.javaee.donation.simulator.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.javaee.donation.common.model.RewardRequest;
import org.junit.jupiter.api.Test;

class ViewerRewardMockDataFactoryTest {

    private final ViewerRewardMockDataFactory factory = new ViewerRewardMockDataFactory();

    @Test
    void shouldGenerateViewerAndStreamerWithinRange() {
        RewardRequest first = factory.next(0, 300000, 100, null);
        RewardRequest last = factory.next(299999, 300000, 100, null);

        assertTrue(first.getViewerId().startsWith("viewer-"));
        assertTrue(first.getStreamerId().startsWith("streamer-"));
        assertEquals("viewer-1", first.getViewerId());
        assertEquals("viewer-300000", last.getViewerId());
        assertEquals("streamer-100", last.getStreamerId());
    }

    @Test
    void shouldUseFixedStreamerWhenProvided() {
        RewardRequest request = factory.next(5, 100, 100, "streamer-42");
        assertEquals("streamer-42", request.getStreamerId());
    }

    @Test
    void shouldGenerateUniqueRewardNo() {
        RewardRequest a = factory.next(1, 100, 10, null);
        RewardRequest b = factory.next(2, 100, 10, null);
        assertNotEquals(a.getRewardNo(), b.getRewardNo());
    }
}
