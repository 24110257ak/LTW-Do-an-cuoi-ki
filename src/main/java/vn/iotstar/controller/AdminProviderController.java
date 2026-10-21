package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.DeliveryProvider;
import vn.iotstar.service.DeliveryProviderService;

import java.math.BigDecimal;

@Controller
@RequestMapping("/admin/providers")
@RequiredArgsConstructor
public class AdminProviderController {

    private final DeliveryProviderService providerService;

    @GetMapping
    public String listProviders(Model model) {
        model.addAttribute("providers", providerService.getAllProviders());
        model.addAttribute("newProvider", new DeliveryProvider());
        return "admin/providers/list";
    }

    @PostMapping("/save")
    public String saveProvider(
            @ModelAttribute("newProvider") DeliveryProvider provider,
            RedirectAttributes redirectAttributes) {

        if (provider.getBaseFee() == null) {
            provider.setBaseFee(BigDecimal.valueOf(30000));
        }
        if (provider.getIsActive() == null) {
            provider.setIsActive(true);
        }

        providerService.saveProvider(provider);
        redirectAttributes.addFlashAttribute("successMessage", "Lưu đơn vị vận chuyển thành công!");
        return "redirect:/admin/providers";
    }

    @PostMapping("/{id}/toggle")
    public String toggleStatus(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        providerService.toggleProviderStatus(id);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái đơn vị vận chuyển thành công!");
        return "redirect:/admin/providers";
    }
}
