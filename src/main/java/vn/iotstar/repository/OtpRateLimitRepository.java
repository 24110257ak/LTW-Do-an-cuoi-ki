package vn.iotstar.repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.iotstar.entity.OtpRateLimit;
import java.util.Optional;
public interface OtpRateLimitRepository extends JpaRepository<OtpRateLimit,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OtpRateLimit r where r.limitKey=:key")
    Optional<OtpRateLimit> lock(@Param("key") String key);
}
