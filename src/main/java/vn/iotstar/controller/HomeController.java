package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.iotstar.service.BannerService;
import vn.iotstar.service.BranchService;
import vn.iotstar.service.CategoryService;
import vn.iotstar.service.ProductService;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final BannerService bannerService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final BranchService branchService;

    @GetMapping({"/", "/index"})
    public String index(Model model) {
        model.addAttribute("banners", bannerService.getActiveBanners());
        model.addAttribute("rootCategories", categoryService.getRootCategories());
        model.addAttribute("featuredProducts", productService.getFeaturedProducts());
        // Sản phẩm bán chạy (>10 sản phẩm sắp xếp giảm dần theo yêu cầu đề bài)
        model.addAttribute("bestSellerProducts", productService.getBestSellerProducts());
        model.addAttribute("topRatedProducts", productService.getTopRatedProducts());
        model.addAttribute("branches", branchService.getAllActiveBranches());
        return "customer/index";
    }
}
