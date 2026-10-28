package vn.iotstar.controller.staff;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.iotstar.service.BranchService;

@Controller
@RequiredArgsConstructor
public class StaffChatController {
    private final BranchService branches;
    @GetMapping("/staff/chat")
    public String inbox(HttpSession session, Model model) {
        boolean admin = branches.isCurrentAdmin();
        Integer branchId = admin ? (Integer) session.getAttribute("staffBranchId") : branches.getStaffBranchId();
        if (admin && (branchId == null || branches.getBranchById(branchId).filter(b -> Boolean.TRUE.equals(b.getIsActive())).isEmpty()))
            branchId = branches.getAllActiveBranches().stream().findFirst().map(b -> b.getBranchId()).orElse(null);
        model.addAttribute("chatCanSelectBranch", admin);
        model.addAttribute("chatBranchId", branchId);
        model.addAttribute("currentBranch", branchId == null ? null : branches.getBranchById(branchId).orElse(null));
        return "staff/chat";
    }
}
