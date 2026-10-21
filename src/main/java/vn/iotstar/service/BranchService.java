package vn.iotstar.service;

import vn.iotstar.entity.Branch;

import java.util.List;
import java.util.Optional;

public interface BranchService {
    List<Branch> getAllActiveBranches();
    Optional<Branch> getBranchById(Integer id);
}
