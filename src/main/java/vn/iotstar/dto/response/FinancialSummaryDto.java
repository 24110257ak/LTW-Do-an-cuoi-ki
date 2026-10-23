package vn.iotstar.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO tổng hợp tài chính, giá vốn (COGS), lợi nhuận gộp và dòng tiền COD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialSummaryDto implements Serializable {
    private BigDecimal totalRevenue;
    private BigDecimal totalCost;
    private BigDecimal totalGrossProfit;
    private Double grossProfitMarginPercent; // Tỷ suất lợi nhuận gộp (%)
    private BigDecimal pendingCodAmount;     // Tiền COD shipper đã thu nhưng chưa đối soát vào quỹ
    private BigDecimal reconciledCodAmount;  // Tiền COD đã đối soát thành công
    private Long totalOrdersCount;
}
