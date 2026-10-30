package vn.iotstar.service;

import vn.iotstar.dto.response.BranchProfitabilityDto;
import vn.iotstar.dto.response.FinancialSummaryDto;
import vn.iotstar.dto.response.TransferSuggestionDto;
import vn.iotstar.entity.BranchInventory;

import java.util.List;

public interface FinancialReportService {
    List<vn.iotstar.dto.response.CompletedDayDto> getCompletedDays(java.time.LocalDate from, java.time.LocalDate to);
    /**
     * Báo cáo tổng hợp tài chính toàn chuỗi: Doanh thu, Giá vốn (COGS), Lợi nhuận gộp, COD
     */
    FinancialSummaryDto getFinancialOverview();

    /**
     * Báo cáo hiệu quả kinh doanh & biên lợi nhuận phân bổ theo từng chi nhánh
     */
    List<BranchProfitabilityDto> getBranchProfitabilityList();

    /**
     * Danh sách cảnh báo hàng dưới ngưỡng an toàn (Safety Stock Alerts)
     */
    List<BranchInventory> getLowStockAlerts();

    /**
     * Thuật toán tự động phân tích và sinh danh sách đề xuất điều chuyển kho thông minh
     * Giúp cân bằng tồn kho giữa các chi nhánh thiếu và thừa hàng
     */
    List<TransferSuggestionDto> generateTransferSuggestions();
}
