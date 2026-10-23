package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.dto.response.BranchProfitabilityDto;
import vn.iotstar.dto.response.FinancialSummaryDto;
import vn.iotstar.dto.response.TransferSuggestionDto;
import vn.iotstar.entity.Branch;
import vn.iotstar.entity.BranchInventory;
import vn.iotstar.repository.BranchInventoryRepository;
import vn.iotstar.repository.BranchRepository;
import vn.iotstar.repository.OrderRepository;
import vn.iotstar.service.FinancialReportService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialReportServiceImpl implements FinancialReportService {

    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final BranchInventoryRepository inventoryRepository;

    @Override
    public FinancialSummaryDto getFinancialOverview() {
        BigDecimal totalRevenue = orderRepository.sumTotalRevenue();
        BigDecimal totalCost = orderRepository.sumTotalCost();
        BigDecimal totalGrossProfit = orderRepository.sumTotalGrossProfit();
        BigDecimal pendingCod = orderRepository.sumPendingCodReconciliationAmount();
        BigDecimal reconciledCod = orderRepository.sumReconciledCodAmount();
        long totalOrders = orderRepository.count();

        double marginPercent = 0.0;
        if (totalRevenue != null && totalRevenue.compareTo(BigDecimal.ZERO) > 0 && totalGrossProfit != null) {
            marginPercent = totalGrossProfit.divide(totalRevenue, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
        }

        return FinancialSummaryDto.builder()
                .totalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .totalCost(totalCost != null ? totalCost : BigDecimal.ZERO)
                .totalGrossProfit(totalGrossProfit != null ? totalGrossProfit : BigDecimal.ZERO)
                .grossProfitMarginPercent(marginPercent)
                .pendingCodAmount(pendingCod != null ? pendingCod : BigDecimal.ZERO)
                .reconciledCodAmount(reconciledCod != null ? reconciledCod : BigDecimal.ZERO)
                .totalOrdersCount(totalOrders)
                .build();
    }

    @Override
    public List<BranchProfitabilityDto> getBranchProfitabilityList() {
        List<Branch> branches = branchRepository.findAll();
        List<BranchProfitabilityDto> result = new ArrayList<>();

        for (Branch b : branches) {
            BigDecimal revenue = orderRepository.sumRevenueByBranch(b.getBranchId());
            BigDecimal cost = orderRepository.sumTotalCostByBranch(b.getBranchId());
            BigDecimal profit = orderRepository.sumGrossProfitByBranch(b.getBranchId());
            long count = orderRepository.countByBranchBranchId(b.getBranchId());

            revenue = revenue != null ? revenue : BigDecimal.ZERO;
            cost = cost != null ? cost : BigDecimal.ZERO;
            profit = profit != null ? profit : BigDecimal.ZERO;

            double margin = 0.0;
            if (revenue.compareTo(BigDecimal.ZERO) > 0) {
                margin = profit.divide(revenue, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
            }

            result.add(BranchProfitabilityDto.builder()
                    .branchId(b.getBranchId())
                    .branchName(b.getBranchName())
                    .province(b.getProvince())
                    .district(b.getDistrict())
                    .revenue(revenue)
                    .cost(cost)
                    .grossProfit(profit)
                    .grossProfitMarginPercent(margin)
                    .orderCount(count)
                    .build());
        }

        return result;
    }

    @Override
    public List<BranchInventory> getLowStockAlerts() {
        return inventoryRepository.findLowStockAlerts();
    }

    @Override
    public List<TransferSuggestionDto> generateTransferSuggestions() {
        List<BranchInventory> lowStocks = inventoryRepository.findLowStockAlerts();
        List<TransferSuggestionDto> suggestions = new ArrayList<>();

        for (BranchInventory targetInv : lowStocks) {
            Long variantId = targetInv.getVariant().getVariantId();
            Integer targetBranchId = targetInv.getBranch().getBranchId();
            int currentStock = targetInv.getQuantity() != null ? targetInv.getQuantity() : 0;
            int safetyStock = targetInv.getSafetyStock() != null ? targetInv.getSafetyStock() : 3;
            int neededQuantity = Math.max(1, (safetyStock * 2) - currentStock);

            // Tìm chi nhánh khác có số lượng tồn kho dôi dư vượt safetyStock
            List<BranchInventory> surplusCandidates = inventoryRepository
                    .findByVariantVariantIdAndQuantityGreaterThanOrderByQuantityDesc(variantId, safetyStock + 1);

            for (BranchInventory candidate : surplusCandidates) {
                if (candidate.getBranch().getBranchId().equals(targetBranchId)) {
                    continue;
                }

                int candidateSafety = candidate.getSafetyStock() != null ? candidate.getSafetyStock() : 3;
                int surplusAvailable = candidate.getQuantity() - candidateSafety;

                if (surplusAvailable > 0) {
                    int transferQty = Math.min(surplusAvailable, neededQuantity);
                    if (transferQty > 0) {
                        String urgency = (currentStock == 0) ? "CRITICAL" : "WARNING";
                        String reason = String.format("Kho %s thiếu hàng (%d/%d an toàn), đề xuất nhận %d sp từ kho %s (dôi dư %d sp)",
                                targetInv.getBranch().getBranchName(),
                                currentStock,
                                safetyStock,
                                transferQty,
                                candidate.getBranch().getBranchName(),
                                surplusAvailable);

                        suggestions.add(TransferSuggestionDto.builder()
                                .variantId(variantId)
                                .productName(targetInv.getVariant().getProduct().getProductName())
                                .variantInfo(targetInv.getVariant().getVariantInfo())
                                .sku(targetInv.getVariant().getSku())
                                .sourceBranchId(candidate.getBranch().getBranchId())
                                .sourceBranchName(candidate.getBranch().getBranchName())
                                .sourceCurrentStock(candidate.getQuantity())
                                .targetBranchId(targetBranchId)
                                .targetBranchName(targetInv.getBranch().getBranchName())
                                .targetCurrentStock(currentStock)
                                .targetSafetyStock(safetyStock)
                                .suggestedQuantity(transferQty)
                                .urgency(urgency)
                                .reason(reason)
                                .build());
                        // Mỗi chi nhánh thiếu hàng chỉ ghép với 1 chi nhánh nguồn dồi dào nhất trong 1 lượt đề xuất
                        break;
                    }
                }
            }
        }

        return suggestions;
    }
}
