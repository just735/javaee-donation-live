package com.javaee.donation.common.model;

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
}
