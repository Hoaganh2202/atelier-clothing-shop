package com.shop.backend.user;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @EntityGraph(attributePaths = {"role"})
    Optional<User> findWithRoleById(Long id);

    @Override
    @EntityGraph(attributePaths = {"role"})
    Page<User> findAll(Specification<User> spec, Pageable pageable);

    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndIdNot(String phone, Long id);

    @Query("SELECT COUNT(u) FROM User u WHERE LOWER(u.role.name) = 'customer'")
    long countTotalCustomers();

    @Query("SELECT COUNT(u) FROM User u WHERE LOWER(u.role.name) = 'customer' AND u.createdAt >= :start AND u.createdAt < :end")
    long countNewCustomersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
