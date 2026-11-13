package com.fintechfive.ftrade.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day on the dashboard's performance chart.
 *
 * @param value market value of the account's holdings at that day's close (today: at the current price)
 * @param invested what was paid for the shares held that day
 */
public record PerformancePointDTO(LocalDate date, BigDecimal value, BigDecimal invested) {
}
