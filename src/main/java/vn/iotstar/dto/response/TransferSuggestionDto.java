package vn.iotstar.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO gợi ý điều chuyển hàng hóa thông minh giữa các chi nhánh
 * Dựa trên ngưỡng tồn kho an toàn (safety_stock) và chi nhánh có hàng dôi dư
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferSuggestionDto implements Serializable {
    private Long variantId;
    private String productName;
    private String variantInfo;
    private String sku;

    private Integer sourceBranchId;
    private String sourceBranchName;
    private Integer sourceCurrentStock;

    private Integer targetBranchId;
    private String targetBranchName;
    private Integer targetCurrentStock;
    private Integer targetSafetyStock;

    private Integer suggestedQuantity;
    private String urgency; // CRITICAL (hết hàng = 0) hoặc WARNING (dưới ngưỡng an toàn)
    private String reason;
}
