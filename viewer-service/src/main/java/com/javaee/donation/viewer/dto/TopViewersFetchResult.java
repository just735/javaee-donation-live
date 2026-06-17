package com.javaee.donation.viewer.dto;

import com.javaee.donation.common.model.TopViewerResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopViewersFetchResult {

    private List<TopViewerResponse> viewers;
    private boolean degraded;
    private String hintMessage;
}
