package vn.iotstar.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.Banner;
import vn.iotstar.service.BannerService;
import vn.iotstar.service.CloudinaryService;

@Controller
@RequestMapping("/admin/banners")
@RequiredArgsConstructor
public class AdminBannerController {

    private final BannerService bannerService;
    private final CloudinaryService cloudinaryService;

    @GetMapping
    public String listBanners(Model model) {
        model.addAttribute("banners", bannerService.getAllBanners());
        model.addAttribute("newBanner", new Banner());
        return "admin/banners/list";
    }

    @PostMapping("/save")
    public String saveBanner(
            @ModelAttribute("newBanner") Banner banner,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            RedirectAttributes redirectAttributes) {

        if (imageFile != null && !imageFile.isEmpty()) {
            String uploadedUrl = cloudinaryService.uploadFile(imageFile, "banners");
            banner.setImageUrl(uploadedUrl);
        } else if (banner.getBannerId() != null) {
            Banner existing = bannerService.getBannerById(banner.getBannerId()).orElse(null);
            if (existing != null) {
                banner.setImageUrl(existing.getImageUrl());
            }
        }

        if (banner.getDisplayOrder() == null) {
            banner.setDisplayOrder(0);
        }
        if (banner.getIsActive() == null) {
            banner.setIsActive(true);
        }

        bannerService.saveBanner(banner);
        redirectAttributes.addFlashAttribute("successMessage", "Lưu banner quảng cáo thành công!");
        return "redirect:/admin/banners";
    }

    @PostMapping("/{id}/toggle")
    public String toggleStatus(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        bannerService.toggleBannerStatus(id);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái banner thành công!");
        return "redirect:/admin/banners";
    }

    @PostMapping("/{id}/delete")
    public String deleteBanner(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        bannerService.deleteBanner(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa banner!");
        return "redirect:/admin/banners";
    }
}
