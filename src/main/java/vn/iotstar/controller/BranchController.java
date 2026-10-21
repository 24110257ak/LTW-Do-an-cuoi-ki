package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.iotstar.service.BranchService;

@Controller
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping("/branches")
    public String listBranches(Model model) {
        model.addAttribute("branches", branchService.getAllActiveBranches());
        return "customer/branch-list";
    }
}
