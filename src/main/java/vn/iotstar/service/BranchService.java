package vn.iotstar.service;

import vn.iotstar.entity.Branch;

import java.util.List;
import java.util.Optional;

public interface BranchService {
    // Management scope: always resolve the current account again from the database.
    void assignStaffBranch(Long userId, Integer branchId);
    boolean isCurrentAdmin();
    boolean isCurrentStaff();
    Integer getStaffBranchId();
    void checkBranchAccess(Integer branchId);
    void checkStaffBranchAccess(Integer branchId);
    List<Branch> getAllBranches();
    List<Branch> getAllActiveBranches();
    Optional<Branch> getBranchById(Integer id);
    Branch saveBranch(Branch branch);
    void toggleBranchStatus(Integer id);
}
