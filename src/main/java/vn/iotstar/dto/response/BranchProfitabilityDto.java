package vn.iotstar.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Báo cáo doanh thu, giá vốn và lợi nhuận gộp theo từng chi nhánh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchProfitabilityDto implements Serializable {
    private Integer branchId;
    private String branchName;
    private String province;
    private String district;
    private BigDecimal revenue;
    private BigDecimal cost;
    private BigDecimal grossProfit;
    private Double grossProfitMarginPercent;
    private Long orderCount;
}
