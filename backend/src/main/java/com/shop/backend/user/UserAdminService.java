package com.shop.backend.user;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.user.dto.CustomerDetailResponse;
import com.shop.backend.user.dto.CustomerListItemResponse;
import com.shop.backend.user.dto.UpdateCustomerStatusRequest;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAdminService {

    private static final String CUSTOMER_ROLE_NAME = "customer";
    private static final char LIKE_ESCAPE_CHAR = '\\';
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "email", "phone", "status", "createdAt", "id");

    private final UserRepository userRepository;

    public PageResponse<CustomerListItemResponse> getCustomers(
            String keyword,
            UserStatus status,
            Pageable pageable
    ) {
        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.min(Math.max(1, pageable.getPageSize()), 100);

        for (Sort.Order order : pageable.getSort()) {
            if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                throw new BadRequestException(
                        "Trường sắp xếp không hợp lệ: '" + order.getProperty() +
                        "'. Chỉ chấp nhận: " + String.join(", ", ALLOWED_SORT_FIELDS));
            }
        }

        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "createdAt");

        Pageable safePageable = PageRequest.of(pageNumber, pageSize, sort);

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(cb.lower(root.get("role").get("name")), CUSTOMER_ROLE_NAME));

            if (StringUtils.hasText(keyword)) {
                String escapedKeyword = escapeLikeWildcards(keyword.trim().toLowerCase());
                String searchPattern = "%" + escapedKeyword + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), searchPattern, LIKE_ESCAPE_CHAR);
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), searchPattern, LIKE_ESCAPE_CHAR);
                Predicate phoneMatch = cb.like(cb.lower(root.get("phone")), searchPattern, LIKE_ESCAPE_CHAR);
                predicates.add(cb.or(nameMatch, emailMatch, phoneMatch));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<User> page = userRepository.findAll(spec, safePageable);
        if (page.isEmpty()) {
            return PageResponse.<CustomerListItemResponse>builder()
                    .content(Collections.emptyList())
                    .page(safePageable.getPageNumber())
                    .size(safePageable.getPageSize())
                    .totalElements(0)
                    .totalPages(0)
                    .build();
        }

        return PageResponse.of(page.map(this::mapToListItemResponse));
    }

    public CustomerDetailResponse getCustomerById(Long id) {
        User user = userRepository.findWithRoleById(id)
                .filter(this::isCustomerAccount)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với id: " + id));

        return mapToDetailResponse(user);
    }

    @Transactional
    public CustomerDetailResponse updateCustomerStatus(
            Long id,
            UpdateCustomerStatusRequest request,
            Long currentAdminId
    ) {
        if (currentAdminId != null && currentAdminId.equals(id)) {
            throw new BadRequestException("Không thể tự khóa hoặc thay đổi trạng thái tài khoản đang đăng nhập");
        }

        User user = userRepository.findWithRoleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với id: " + id));

        if (!isCustomerAccount(user)) {
            throw new BadRequestException("Chỉ được phép thay đổi trạng thái đối với tài khoản khách hàng");
        }

        user.setStatus(request.getStatus());
        User savedUser = userRepository.save(user);

        return mapToDetailResponse(savedUser);
    }

    public long countTotalCustomers() {
        return userRepository.countTotalCustomers();
    }

    public long countNewCustomersBetween(LocalDateTime start, LocalDateTime end) {
        return userRepository.countNewCustomersBetween(start, end);
    }

    private String escapeLikeWildcards(String input) {
        // Escape SQL LIKE special characters (\, %, _) so user input is matched literally.
        return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private boolean isCustomerAccount(User user) {
        return user.getRole() != null && CUSTOMER_ROLE_NAME.equalsIgnoreCase(user.getRole().getName());
    }

    private CustomerListItemResponse mapToListItemResponse(User user) {
        return CustomerListItemResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private CustomerDetailResponse mapToDetailResponse(User user) {
        return CustomerDetailResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .gender(user.getGender())
                .birthDate(user.getBirthDate())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .dataSource(user.getDataSource())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
