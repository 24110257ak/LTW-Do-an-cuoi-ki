package vn.iotstar.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.iotstar.dto.response.ApiResponse;
import vn.iotstar.dto.response.BranchProfitabilityDto;
import vn.iotstar.dto.response.FinancialSummaryDto;
import vn.iotstar.dto.response.TransferSuggestionDto;
import vn.iotstar.entity.BranchInventory;
import vn.iotstar.service.FinancialReportService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/financial")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Quản trị Tài chính & Tồn kho (Financial & Inventory BI API)", description = "Các API phân tích kinh doanh, giá vốn COGS, biên lợi nhuận, đối soát COD và cân bằng tồn kho thông minh")
public class FinancialApiController {

    private final FinancialReportService financialReportService;

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Báo cáo tổng quan tài chính toàn chuỗi", description = "Tính toán doanh thu thuần, tổng giá vốn COGS, lợi nhuận gộp và tiền mặt COD")
    public ResponseEntity<ApiResponse<FinancialSummaryDto>> getFinancialOverview() {
        FinancialSummaryDto summary = financialReportService.getFinancialOverview();
        return ResponseEntity.ok(ApiResponse.ok("Lấy báo cáo tài chính tổng quan thành công", summary));
    }

    @GetMapping("/branch-profitability")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Báo cáo doanh thu & tỷ suất lợi nhuận từng chi nhánh", description = "Phân tích P&L từng chi nhánh cửa hàng giúp đưa ra quyết định kinh doanh")
    public ResponseEntity<ApiResponse<List<BranchProfitabilityDto>>> getBranchProfitability() {
        List<BranchProfitabilityDto> list = financialReportService.getBranchProfitabilityList();
        return ResponseEntity.ok(ApiResponse.ok("Lấy báo cáo lợi nhuận chi nhánh thành công", list));
    }

    @GetMapping("/low-stock-alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Cảnh báo tồn kho dưới ngưỡng an toàn (Safety Stock Alerts)", description = "Liệt kê danh sách hàng hóa sắp hết hàng cần bổ sung")
    public ResponseEntity<ApiResponse<List<BranchInventory>>> getLowStockAlerts() {
        List<BranchInventory> alerts = financialReportService.getLowStockAlerts();
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách cảnh báo tồn kho thành công", alerts));
    }

    @GetMapping("/transfer-suggestions")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Đề xuất điều chuyển kho thông minh (Smart Inventory Transfer Suggestions)", description = "Thuật toán tự động ghép đôi chi nhánh thiếu hàng với chi nhánh thừa hàng")
    public ResponseEntity<ApiResponse<List<TransferSuggestionDto>>> getTransferSuggestions() {
        List<TransferSuggestionDto> suggestions = financialReportService.generateTransferSuggestions();
        return ResponseEntity.ok(ApiResponse.ok("Sinh đề xuất điều chuyển kho thông minh thành công", suggestions));
    }

    @GetMapping("/daily")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<vn.iotstar.dto.response.CompletedDayDto>>> daily(
            @org.springframework.web.bind.annotation.RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @org.springframework.web.bind.annotation.RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok("Doanh thu hàng hóa theo ngày hoàn tất",financialReportService.getCompletedDays(from,to)));
    }
}
