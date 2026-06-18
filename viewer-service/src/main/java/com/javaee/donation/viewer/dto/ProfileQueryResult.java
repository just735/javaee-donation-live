package com.javaee.donation.viewer.dto;

import com.javaee.donation.common.model.ViewerProfileResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileQueryResult {

    private ViewerProfileResponse profile;
    private boolean degraded;
    private String hintMessage;
}
