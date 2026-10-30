package vn.iotstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.Branch;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select b from Branch b where b.branchId = :id")
    Optional<Branch> findForCheckout(@org.springframework.data.repository.query.Param("id") Integer id);

    List<Branch> findAllByIsActiveTrue();

    Optional<Branch> findByManagerUserId(Long managerId);

    boolean existsByBranchName(String branchName);
}
