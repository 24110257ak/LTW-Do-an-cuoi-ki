package vn.iotstar.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.dto.request.RegisterRequest;
import vn.iotstar.dto.request.ResetPasswordRequest;
import vn.iotstar.dto.request.VerifyOtpRequest;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.service.UserService;

@Slf4j
@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            Model model
    ) {
        if (error != null) {
            model.addAttribute("errorMessage", "Tài khoản hoặc mật khẩu không chính xác, hoặc tài khoản chưa kích hoạt OTP!");
        }
        if (logout != null) {
            model.addAttribute("successMessage", "Bạn đã đăng xuất thành công khỏi hệ thống!");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    @PostMapping("/register")
    public String processRegister(
            @Valid @ModelAttribute("registerRequest") RegisterRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            userService.register(request);
            redirectAttributes.addFlashAttribute("email", request.getEmail());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đăng ký thành công! Mã OTP xác thực 6 chữ số đã được gửi tới email " + request.getEmail() + ". Vui lòng nhập mã để kích hoạt.");
            return "redirect:/auth/verify-otp?email=" + request.getEmail();
        } catch (BadRequestException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/register";
        }
    }

    @GetMapping("/verify-otp")
    public String verifyOtpPage(@RequestParam(required = false) String email, Model model) {
        VerifyOtpRequest request = new VerifyOtpRequest();
        if (email != null) {
            request.setEmail(email);
        }
        model.addAttribute("verifyOtpRequest", request);
        return "auth/verify-otp";
    }

    @PostMapping("/verify-otp")
    public String processVerifyOtp(
            @Valid @ModelAttribute("verifyOtpRequest") VerifyOtpRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/verify-otp";
        }

        try {
            userService.verifyAccount(request.getEmail(), request.getOtpCode());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Kích hoạt tài khoản thành công! Bây giờ bạn có thể đăng nhập vào hệ thống.");
            return "redirect:/auth/login";
        } catch (BadRequestException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/verify-otp";
        }
    }

    @GetMapping("/resend-otp")
    public String resendOtp(@RequestParam String email, RedirectAttributes redirectAttributes) {
        try {
            userService.resendOtp(email);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi lại mã OTP thành công. Vui lòng kiểm tra hộp thư!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/auth/verify-otp?email=" + email;
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email, RedirectAttributes redirectAttributes) {
        try {
            userService.forgotPassword(email);
            redirectAttributes.addFlashAttribute("email", email);
            redirectAttributes.addFlashAttribute("successMessage", "Mã OTP đặt lại mật khẩu đã được gửi đến email của bạn.");
            return "redirect:/auth/reset-password?email=" + email;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/auth/forgot-password";
        }
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String email, Model model) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        if (email != null) {
            request.setEmail(email);
        }
        model.addAttribute("resetPasswordRequest", request);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(
            @Valid @ModelAttribute("resetPasswordRequest") ResetPasswordRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/reset-password";
        }

        try {
            userService.resetPassword(request.getEmail(), request.getOtpCode(), request.getNewPassword());
            redirectAttributes.addFlashAttribute("successMessage", "Đặt lại mật khẩu thành công! Vui lòng đăng nhập với mật khẩu mới.");
            return "redirect:/auth/login";
        } catch (BadRequestException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/reset-password";
        }
    }
}
