package vn.iotstar.service.impl;

import vn.iotstar.exception.BadRequestException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.iotstar.entity.User;
import vn.iotstar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.Branch;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.BranchRepository;
import vn.iotstar.service.BranchService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    private User currentOperator() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Vui lòng đăng nhập");
        }
        var user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không tồn tại"));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AccessDeniedException("Tài khoản đã bị khóa");
        }
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCurrentAdmin() {
        return "ADMIN".equals(currentOperator().getRole().getRoleName());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCurrentStaff() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) return false;
        // A stale STAFF session must still be checked even if its role changed in the DB.
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_STAFF".equals(a.getAuthority()))
                || "STAFF".equals(currentOperator().getRole().getRoleName());
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getStaffBranchId() {
        var user = currentOperator();
        if (!"STAFF".equals(user.getRole().getRoleName()) || user.getBranch() == null
                || !Boolean.TRUE.equals(user.getBranch().getIsActive())) {
            throw new AccessDeniedException(
                    "Nhân viên chưa được gán chi nhánh đang hoạt động. Vui lòng liên hệ quản trị viên.");
        }
        return user.getBranch().getBranchId();
    }

    @Override
    @Transactional(readOnly = true)
    public void checkBranchAccess(Integer branchId) {
        if (isCurrentAdmin()) return;
        if (branchId == null || !getStaffBranchId().equals(branchId)) {
            throw new AccessDeniedException("Bạn không có quyền quản lý chi nhánh này");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void checkStaffBranchAccess(Integer branchId) {
        // Shared methods are also used by customer checkout and internal stock movements.
        if (isCurrentStaff()) checkBranchAccess(branchId);
    }


    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void assignStaffBranch(Long userId, Integer branchId) {
        if (!isCurrentAdmin()) throw new AccessDeniedException("Chỉ admin được phân công chi nhánh");
        var staff = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Nhân viên", "id", userId));
        if (!"STAFF".equals(staff.getRole().getRoleName())) {
            throw new BadRequestException("Chỉ phân công chi nhánh cho tài khoản STAFF");
        }
        Branch branch = null;
        if (branchId != null) {
            branch = branchRepository.findById(branchId)
                    .filter(b -> Boolean.TRUE.equals(b.getIsActive()))
                    .orElseThrow(() -> new BadRequestException("Chi nhánh không tồn tại hoặc đã ngừng hoạt động"));
        }
        staff.setBranch(branch);
        userRepository.save(staff);
    }

    @Override
    public List<Branch> getAllBranches() {
        return branchRepository.findAll();
    }

    @Override
    public List<Branch> getAllActiveBranches() {
        return branchRepository.findAllByIsActiveTrue();
    }

    @Override
    public Optional<Branch> getBranchById(Integer id) {
        return branchRepository.findById(id);
    }

    @Override
    @Transactional
    public Branch saveBranch(Branch branch) {
        return branchRepository.save(branch);
    }

    @Override
    @Transactional
    public void toggleBranchStatus(Integer id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh", "id", id));
        branch.setIsActive(branch.getIsActive() == null || !branch.getIsActive());
        branchRepository.save(branch);
    }
}
