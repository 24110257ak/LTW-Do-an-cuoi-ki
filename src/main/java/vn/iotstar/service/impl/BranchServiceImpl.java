package vn.iotstar.service.impl;

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
