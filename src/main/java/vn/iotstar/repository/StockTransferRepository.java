package vn.iotstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.StockTransfer;
import vn.iotstar.entity.TransferStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    Optional<StockTransfer> findByTransferCode(String transferCode);

    List<StockTransfer> findByStatusOrderByCreatedAtDesc(TransferStatus status);

    List<StockTransfer> findBySourceBranchBranchIdOrDestinationBranchBranchIdOrderByCreatedAtDesc(Integer sourceBranchId, Integer destinationBranchId);

    List<StockTransfer> findAllByOrderByCreatedAtDesc();
}
