package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.Branch;
import vn.iotstar.service.BranchService;

@Controller
@RequestMapping("/admin/branches")
@RequiredArgsConstructor
public class AdminBranchController {

    private final BranchService branchService;

    @GetMapping
    public String listBranches(Model model) {
        model.addAttribute("branches", branchService.getAllBranches());
        model.addAttribute("newBranch", new Branch());
        return "admin/branches/list";
    }

    @PostMapping("/save")
    public String saveBranch(
            @ModelAttribute("newBranch") Branch branch,
            RedirectAttributes redirectAttributes) {

        if (branch.getIsActive() == null) {
            branch.setIsActive(true);
        }

        branchService.saveBranch(branch);
        redirectAttributes.addFlashAttribute("successMessage", "Lưu thông tin chi nhánh thành công!");
        return "redirect:/admin/branches";
    }

    @PostMapping("/{id}/toggle")
    public String toggleStatus(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        branchService.toggleBranchStatus(id);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái chi nhánh thành công!");
        return "redirect:/admin/branches";
    }
}
