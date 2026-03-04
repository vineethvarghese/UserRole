package com.userrole.auth.local;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoredTokenRepository extends JpaRepository<StoredToken, Long> {

    Optional<StoredToken> findByRefreshToken(String refreshToken);

    @Query("SELECT t FROM StoredToken t WHERE t.userId = :userId")
    List<StoredToken> findAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE StoredToken t SET t.revoked = true WHERE t.userId = :userId")
    void revokeAllByUserId(@Param("userId") Long userId);
}
