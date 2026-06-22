package com.javaee.donation.viewer.constant;

public final class ViewerConstants {

    public static final String SERVICE_NAME = "viewer-service";
    public static final String PROFILE_TAG_PENDING = "PENDING";
    public static final String PROFILE_DEGRADED_HINT =
            "画像正在计算中或服务暂时不可用，请稍后再试";
    public static final String TOP_VIEWERS_DEGRADED_HINT =
            "Top10 观众数据暂时不可用，请稍后再试";
    public static final String TASK_STATUS_PENDING = "PENDING";
    public static final String TASK_STATUS_PROCESSING = "PROCESSING";
    public static final String TASK_STATUS_RETRY = "RETRY";
    public static final String TASK_STATUS_SETTLED = "SETTLED";
    public static final String TASK_STATUS_DUPLICATE = "DUPLICATE";

    private ViewerConstants() {
    }
}
