package vn.iotstar.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.Branch;
import vn.iotstar.entity.Coupon;
import vn.iotstar.enums.DiscountType;
import vn.iotstar.service.BranchService;
import vn.iotstar.service.CouponService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;
    private final BranchService branchService;

    @GetMapping
    public String listCoupons(Model model) {
        model.addAttribute("coupons", couponService.getAllCoupons());
        model.addAttribute("branches", branchService.getAllActiveBranches());
        model.addAttribute("discountTypes", DiscountType.values());
        model.addAttribute("newCoupon", new vn.iotstar.dto.request.CouponForm());
        return "admin/coupons/list";
    }

    @PostMapping("/save")
    public String saveCoupon(
            @ModelAttribute("newCoupon") vn.iotstar.dto.request.CouponForm form,
            @RequestParam(value = "branchId", required = false) Integer branchId,
            RedirectAttributes redirectAttributes) {

        Coupon coupon = form.toEntity();
        if (branchId != null && branchId > 0) {
            Branch branch = branchService.getBranchById(branchId).filter(b -> Boolean.TRUE.equals(b.getIsActive()))
                    .orElseThrow(() -> new vn.iotstar.exception.BadRequestException("Chi nhánh không hợp lệ"));
            coupon.setBranch(branch);
        } else {
            coupon.setBranch(null); // Toàn chuỗi
        }

        if (coupon.getMinOrderAmt() == null) {
            coupon.setMinOrderAmt(BigDecimal.ZERO);
        }
        if (coupon.getUsageLimit() == null) {
            coupon.setUsageLimit(100);
        }
        if (coupon.getUsedCount() == null) {
            coupon.setUsedCount(0);
        }
        if (coupon.getValidFrom() == null) {
            coupon.setValidFrom(LocalDateTime.now());
        }
        if (coupon.getValidUntil() == null) {
            coupon.setValidUntil(LocalDateTime.now().plusMonths(1));
        }
        if (coupon.getIsActive() == null) {
            coupon.setIsActive(true);
        }

        try {
            couponService.saveCoupon(coupon);
        } catch (vn.iotstar.exception.BadRequestException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/coupons";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Lưu mã giảm giá thành công!");
        return "redirect:/admin/coupons";
    }

    @PostMapping("/{id}/toggle")
    public String toggleStatus(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        couponService.toggleCouponStatus(id);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái mã giảm giá thành công!");
        return "redirect:/admin/coupons";
    }

    @PostMapping("/{id}/delete")
    public String deleteCoupon(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        couponService.deleteCoupon(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa mã giảm giá!");
        return "redirect:/admin/coupons";
    }
}
