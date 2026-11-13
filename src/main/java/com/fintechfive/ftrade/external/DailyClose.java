package com.fintechfive.ftrade.external;

import java.math.BigDecimal;
import java.time.LocalDate;

// a symbol's closing price on one day, from Fauxnance's daily candles
public record DailyClose(LocalDate date, BigDecimal close) {
}
