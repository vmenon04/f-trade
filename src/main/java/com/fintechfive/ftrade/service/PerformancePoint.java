package com.fintechfive.ftrade.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day of an account's performance.
 *
 * @param value market value of the holdings at that day's close
 * @param invested what was paid for the shares held that day (their average cost)
 */
public record PerformancePoint(LocalDate date, BigDecimal value, BigDecimal invested) {
}
