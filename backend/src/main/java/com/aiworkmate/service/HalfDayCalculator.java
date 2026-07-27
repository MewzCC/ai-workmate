package com.aiworkmate.service;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;

import java.time.LocalDate;

public final class HalfDayCalculator {

    private HalfDayCalculator() {
    }

    public static int calculate(LocalDate startDate,
                                String startPeriod,
                                LocalDate endDate,
                                String endPeriod) {
        long startSlot = Math.addExact(Math.multiplyExact(startDate.toEpochDay(), 2),
                "PM".equals(startPeriod) ? 1 : 0);
        long endSlot = Math.addExact(Math.multiplyExact(endDate.toEpochDay(), 2),
                "PM".equals(endPeriod) ? 1 : 0);
        long halfDays = endSlot - startSlot + 1;
        if (halfDays <= 0 || halfDays > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "请假结束时间不得早于开始时间");
        }
        return (int) halfDays;
    }
}
