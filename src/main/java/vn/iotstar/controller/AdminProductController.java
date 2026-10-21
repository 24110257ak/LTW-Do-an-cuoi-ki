package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.*;
import vn.iotstar.service.*;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final BranchService branchService;
    private final CloudinaryService cloudinaryService;
    private final InventoryService inventoryService;

    @GetMapping
    public String listProducts(
            @RequestParam(value = "categoryId", required = false) Integer categoryId,
            @RequestParam(value = "brandId", required = false) Integer brandId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model) {

        Page<Product> productPage = productService.searchProductsAdmin(
                categoryId, brandId, keyword,
                PageRequest.of(page, size, Sort.by("productId").descending()));

        model.addAttribute("productPage", productPage);
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("brands", brandService.getAllBrands());
        model.addAttribute("selectedCategory", categoryId);
        model.addAttribute("selectedBrand", brandId);
        model.addAttribute("keyword", keyword);

        return "admin/products/list";
    }

    @GetMapping("/new")
    public String newProductForm(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("brands", brandService.getAllBrands());
        return "admin/products/form";
    }

    @GetMapping("/edit/{id}")
    public String editProductForm(@PathVariable("id") Long id, Model model) {
        Product product = productService.getProductById(id).orElseThrow();
        model.addAttribute("product", product);
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("brands", brandService.getAllBrands());
        return "admin/products/form";
    }

    @PostMapping("/save")
    public String saveProduct(
            @ModelAttribute("product") Product product,
            @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnailFile,
            @RequestParam("categoryId") Integer categoryId,
            @RequestParam("brandId") Integer brandId,
            RedirectAttributes redirectAttributes) {

        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            String uploadedUrl = cloudinaryService.uploadFile(thumbnailFile, "products");
            product.setThumbnailUrl(uploadedUrl);
        }

        Category category = categoryService.getCategoryById(categoryId).orElseThrow();
        Brand brand = brandService.getBrandById(brandId).orElseThrow();
        product.setCategory(category);
        product.setBrand(brand);

        if (product.getBasePrice() == null) {
            product.setBasePrice(BigDecimal.ZERO);
        }
        if (product.getTotalSold() == null) {
            product.setTotalSold(0);
        }
        if (product.getIsActive() == null) {
            product.setIsActive(true);
        }
        if (product.getIsFeatured() == null) {
            product.setIsFeatured(false);
        }

        Product saved = productService.saveProduct(product);
        redirectAttributes.addFlashAttribute("successMessage", "Lưu thông tin sản phẩm thành công!");
        return "redirect:/admin/products/" + saved.getProductId() + "/variants";
    }

    @PostMapping("/{id}/toggle")
    public String toggleProductStatus(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        productService.toggleProductStatus(id);
        redirectAttributes.addFlashAttribute("successMessage", "Thay đổi trạng thái sản phẩm thành công!");
        return "redirect:/admin/products";
    }

    @GetMapping("/{id}/variants")
    public String productVariants(@PathVariable("id") Long id, Model model) {
        Product product = productService.getProductById(id).orElseThrow();
        List<ProductVariant> variants = productService.getVariantsByProductId(id);
        List<Branch> branches = branchService.getAllActiveBranches();

        model.addAttribute("product", product);
        model.addAttribute("variants", variants);
        model.addAttribute("branches", branches);
        model.addAttribute("newVariant", new ProductVariant());

        return "admin/products/variants";
    }

    @PostMapping("/{id}/variants/save")
    public String saveVariant(
            @PathVariable("id") Long productId,
            @ModelAttribute("newVariant") ProductVariant variant,
            @RequestParam(value = "variantImageFile", required = false) MultipartFile variantImageFile,
            RedirectAttributes redirectAttributes) {

        Product product = productService.getProductById(productId).orElseThrow();
        variant.setProduct(product);

        if (variantImageFile != null && !variantImageFile.isEmpty()) {
            String uploadedUrl = cloudinaryService.uploadFile(variantImageFile, "products/variants");
            variant.setImageUrl(uploadedUrl);
        } else if (variant.getVariantId() != null) {
            ProductVariant existing = productService.getVariantById(variant.getVariantId()).orElse(null);
            if (existing != null) {
                variant.setImageUrl(existing.getImageUrl());
            }
        }

        if (variant.getIsActive() == null) {
            variant.setIsActive(true);
        }

        ProductVariant savedVariant = productService.saveVariant(variant);

        // Khởi tạo inventory 0 cho tất cả chi nhánh nếu là variant mới
        List<Branch> branches = branchService.getAllActiveBranches();
        for (Branch b : branches) {
            if (inventoryService.getInventoryItem(b.getBranchId(), savedVariant.getVariantId()).isEmpty()) {
                inventoryService.updateStock(b.getBranchId(), savedVariant.getVariantId(), 5);
            }
        }

        redirectAttributes.addFlashAttribute("successMessage", "Lưu biến thể sản phẩm thành công!");
        return "redirect:/admin/products/" + productId + "/variants";
    }

    @PostMapping("/{productId}/variants/{variantId}/delete")
    public String deleteVariant(
            @PathVariable("productId") Long productId,
            @PathVariable("variantId") Long variantId,
            RedirectAttributes redirectAttributes) {

        productService.deleteVariant(variantId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa biến thể!");
        return "redirect:/admin/products/" + productId + "/variants";
    }
}
