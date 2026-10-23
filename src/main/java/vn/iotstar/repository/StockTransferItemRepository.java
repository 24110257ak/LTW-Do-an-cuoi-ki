package vn.iotstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.StockTransferItem;

import java.util.List;

@Repository
public interface StockTransferItemRepository extends JpaRepository<StockTransferItem, Long> {

    List<StockTransferItem> findByTransferTransferId(Long transferId);
}
