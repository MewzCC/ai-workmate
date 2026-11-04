package com.aiworkmate.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Locale-independent standard units for configured upload limits, including small limits. */
public final class FileSizeLimit {
    private FileSizeLimit() { }

    public static String describe(long bytes) {
        long divisor = bytes >= 1048576 ? 1048576 : bytes >= 1024 ? 1024 : 1;
        String unit = divisor == 1048576 ? "MB" : divisor == 1024 ? "KB" : "B";
        return BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + unit;
    }
}
