package com.shop.backend.address;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    @Query("SELECT a FROM UserAddress a WHERE a.user.id = :userId ORDER BY a.isDefault DESC, a.id DESC")
    List<UserAddress> findAllByUserIdOrdered(@Param("userId") Long userId);

    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);

    // Pessimistic write lock serializes concurrent address mutations for the same user
    // so the single-default invariant cannot be violated by parallel requests.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM UserAddress a WHERE a.user.id = :userId ORDER BY a.id DESC")
    List<UserAddress> findAllByUserIdForUpdate(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserAddress a SET a.isDefault = false WHERE a.user.id = :userId AND a.id <> :addressId")
    int clearOtherDefaults(@Param("userId") Long userId, @Param("addressId") Long addressId);
}
