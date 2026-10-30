package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.service.PaymentSimulationService;
import vn.iotstar.util.SecurityUtils;

@Controller @RequiredArgsConstructor
@RequestMapping("/checkout/vnpay-sim")
public class PaymentSimulationController {
    private final PaymentSimulationService simulator;
    private Long userId() { return SecurityUtils.getCurrentUser().orElseThrow(() -> new BadRequestException("Vui lòng đăng nhập")).getUserId(); }

    @GetMapping("/{orderCode}")
    public String show(@PathVariable String orderCode, Model model) {
        var payment = simulator.view(userId(),orderCode);
        model.addAttribute("payment",payment);
        model.addAttribute("order",payment.getOrder());
        return "customer/vnpay-simulation";
    }

    @PostMapping("/{orderCode}")
    public String pay(@PathVariable String orderCode, @RequestParam PaymentSimulationService.Outcome outcome,
                      RedirectAttributes redirect) {
        var payment = simulator.settle(userId(),orderCode,outcome);
        redirect.addFlashAttribute("paymentMessage",payment.getStatus().getDisplayName());
        return "redirect:/checkout/vnpay-sim/" + orderCode;
    }
}
