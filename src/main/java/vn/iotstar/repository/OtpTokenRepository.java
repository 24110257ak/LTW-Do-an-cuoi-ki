package vn.iotstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.OtpToken;
import vn.iotstar.enums.OtpPurpose;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {
    Optional<OtpToken> findTopByEmailAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(String email, OtpPurpose purpose);

    @Modifying
    @Query("UPDATE OtpToken o SET o.isUsed = true WHERE o.email = :email AND o.purpose = :purpose")
    void invalidateOldTokens(@Param("email") String email, @Param("purpose") OtpPurpose purpose);
}

