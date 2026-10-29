package com.example.otaconsole.support;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 平台业务时间统一按北京时间记录。
 * 不依赖 JVM 默认时区，容器未设置 TZ 时也不会写成 UTC。
 */
public final class ApplicationTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private ApplicationTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
