package vn.iotstar.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CompletedDayDto(LocalDate date, BigDecimal revenue, BigDecimal cost, BigDecimal grossProfit) {}
