package vn.iotstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.Branch;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Integer> {
    List<Branch> findAllByIsActiveTrue();
    Optional<Branch> findByManagerUserId(Long managerId);
    boolean existsByBranchName(String branchName);
}
