package com.fintechfive.ftrade.enums;

import java.time.LocalDate;

// how far back the performance chart goes
public enum PerformanceRange {
    ONE_MONTH, THREE_MONTHS, ONE_YEAR, ALL;

    // the first day the chart covers, or null for everything since the first trade
    public LocalDate startFrom(LocalDate today) {
        return switch (this) {
            case ONE_MONTH -> today.minusMonths(1);
            case THREE_MONTHS -> today.minusMonths(3);
            case ONE_YEAR -> today.minusYears(1);
            case ALL -> null;
        };
    }
}
