package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.Brand;
import vn.iotstar.service.BrandService;
import vn.iotstar.service.CloudinaryService;

@Controller
@RequestMapping("/admin/brands")
@RequiredArgsConstructor
public class AdminBrandController {

    private final BrandService brandService;
    private final CloudinaryService cloudinaryService;

    @GetMapping
    public String listBrands(Model model) {
        model.addAttribute("brands", brandService.getAllBrands());
        model.addAttribute("newBrand", new Brand());
        return "admin/brands/list";
    }

    @PostMapping("/save")
    public String saveBrand(
            @ModelAttribute("newBrand") Brand brand,
            @RequestParam(value = "logoFile", required = false) MultipartFile logoFile,
            RedirectAttributes redirectAttributes) {

        if (logoFile != null && !logoFile.isEmpty()) {
            String uploadedUrl = cloudinaryService.uploadFile(logoFile, "brands");
            brand.setLogoUrl(uploadedUrl);
        } else if (brand.getBrandId() != null) {
            Brand existing = brandService.getBrandById(brand.getBrandId()).orElse(null);
            if (existing != null) {
                brand.setLogoUrl(existing.getLogoUrl());
            }
        }

        if (brand.getIsActive() == null) {
            brand.setIsActive(true);
        }

        brandService.saveBrand(brand);
        redirectAttributes.addFlashAttribute("successMessage", "Lưu thương hiệu thành công!");
        return "redirect:/admin/brands";
    }

    @PostMapping("/{id}/toggle")
    public String toggleStatus(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        brandService.toggleBrandStatus(id);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái thương hiệu thành công!");
        return "redirect:/admin/brands";
    }
}
