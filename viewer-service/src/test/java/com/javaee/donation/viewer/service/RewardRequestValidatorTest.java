package com.javaee.donation.viewer.service;

import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.viewer.exception.ViewerBusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RewardRequestValidatorTest {

    private final RewardRequestValidator validator = new RewardRequestValidator();

    @Test
    void shouldRejectBlankRewardNo() {
        RewardRequest request = validRequest();
        request.setRewardNo("");

        ViewerBusinessException exception = assertThrows(ViewerBusinessException.class,
                () -> validator.validate(request));
        assertEquals("INVALID_REWARD_NO", exception.getCode());
    }

    @Test
    void shouldRejectNonPositiveAmount() {
        RewardRequest request = validRequest();
        request.setRewardAmount(BigDecimal.ZERO);

        ViewerBusinessException exception = assertThrows(ViewerBusinessException.class,
                () -> validator.validate(request));
        assertEquals("INVALID_AMOUNT", exception.getCode());
    }

    private RewardRequest validRequest() {
        RewardRequest request = new RewardRequest();
        request.setRewardNo("r-001");
        request.setViewerId("v-1");
        request.setStreamerId("s-1");
        request.setRewardAmount(new BigDecimal("10.00"));
        return request;
    }
}
