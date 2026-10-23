package vn.iotstar.service;

import vn.iotstar.entity.StockTransfer;
import vn.iotstar.entity.TransferStatus;
import vn.iotstar.entity.User;

import java.util.List;
import java.util.Map;

public interface StockTransferService {

    StockTransfer createTransfer(Integer sourceBranchId, Integer destinationBranchId,
                                 Map<Long, Integer> variantQuantities, User actor, String note);

    List<StockTransfer> getTransfersByBranch(Integer branchId);

    List<StockTransfer> getAllTransfers();

    List<StockTransfer> getTransfersByStatus(TransferStatus status);

    StockTransfer getTransferById(Long transferId);

    void approveTransfer(Long transferId, User actor);

    void shipTransfer(Long transferId, User actor);

    void completeTransfer(Long transferId, User actor);

    void cancelTransfer(Long transferId, User actor, String reason);
}
