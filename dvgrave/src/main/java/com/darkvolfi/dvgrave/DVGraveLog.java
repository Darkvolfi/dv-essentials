package com.darkvolfi.dvgrave;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** DV Grave's log, for compatibility code that falls back quietly when another mod doesn't cooperate. */
public final class DVGraveLog {
    private static final Logger LOG = LogUtils.getLogger();

    private DVGraveLog() {}

    public static void warn(String message, Object... args) {
        LOG.warn("[DV Grave] " + message, args);
    }
}
