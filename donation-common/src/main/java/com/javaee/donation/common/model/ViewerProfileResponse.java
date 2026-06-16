package com.javaee.donation.common.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewerProfileResponse {

    private String viewerId;
    private String viewerName;
    private String profileTag;
    private BigDecimal profileScore;

    public ViewerProfileResponse(String viewerId, String viewerName, String profileTag) {
        this.viewerId = viewerId;
        this.viewerName = viewerName;
        this.profileTag = profileTag;
    }
}
