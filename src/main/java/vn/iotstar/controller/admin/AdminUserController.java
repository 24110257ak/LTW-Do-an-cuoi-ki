package vn.iotstar.controller.admin;

import vn.iotstar.service.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.User;
import vn.iotstar.service.UserService;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final BranchService branchService;

    @GetMapping
    public String listUsers(
            @RequestParam(value = "roleId", required = false) Integer roleId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "15") int size,
            Model model) {

        Page<User> userPage = userService.searchUsersAdmin(
                roleId, keyword,
                PageRequest.of(page, size, Sort.by("userId").descending()));

        model.addAttribute("userPage", userPage);
        model.addAttribute("staffAssignmentBranches", branchService.getAllActiveBranches());
        model.addAttribute("roles", userService.getAllRoles());
        model.addAttribute("selectedRole", roleId);
        model.addAttribute("keyword", keyword);

        return "admin/users/list";
    }

    @PostMapping("/{id}/branch")
    public String assignBranch(@PathVariable("id") Long id,
                               @RequestParam(value = "branchId", required = false) Integer branchId,
                               RedirectAttributes redirectAttributes) {
        branchService.assignStaffBranch(id, branchId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật chi nhánh phụ trách của nhân viên");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/toggle")
    public String toggleStatus(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        userService.toggleUserStatus(id);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái tài khoản thành công!");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/role")
    public String updateRole(
            @PathVariable("id") Long id,
            @RequestParam("roleId") Integer roleId,
            RedirectAttributes redirectAttributes) {

        userService.updateUserRole(id, roleId);
        redirectAttributes.addFlashAttribute("successMessage", "Phân quyền tài khoản thành công!");
        return "redirect:/admin/users";
    }
}
