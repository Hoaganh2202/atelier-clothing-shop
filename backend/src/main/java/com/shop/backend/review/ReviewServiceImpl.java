package com.shop.backend.review;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ConflictException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.product.Product;
import com.shop.backend.product.ProductRepository;
import com.shop.backend.review.dto.CreateReviewRequest;
import com.shop.backend.review.dto.ReviewEligibilityResponse;
import com.shop.backend.review.dto.ReviewResponse;
import com.shop.backend.review.dto.ReviewSummaryResponse;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listReviews(Long productId, int page, int size) {
        ensureActiveProductExists(productId);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<ReviewResponse> reviewPage = reviewRepository.findByProductId(productId, pageable).map(ReviewResponse::from);
        return PageResponse.of(reviewPage);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewSummaryResponse getReviewSummary(Long productId) {
        ensureActiveProductExists(productId);
        Map<Integer, Long> distribution = new TreeMap<>();
        for (int star = 1; star <= 5; star++) {
            distribution.put(star, 0L);
        }
        long count = 0;
        long sum = 0;
        for (Object[] row : reviewRepository.countByRatingGrouped(productId)) {
            int rating = ((Number) row[0]).intValue();
            long c = ((Number) row[1]).longValue();
            distribution.put(rating, c);
            count += c;
            sum += (long) rating * c;
        }
        double average = count == 0 ? 0.0 : Math.round(sum * 10.0 / count) / 10.0;
        return new ReviewSummaryResponse(average, count, distribution);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewEligibilityResponse checkEligibility(String userEmail, Long productId) {
        ensureActiveProductExists(productId);
        if (userEmail == null || userEmail.isBlank()) {
            return new ReviewEligibilityResponse(false, "NOT_LOGGED_IN");
        }
        User user = userRepository.findByEmail(userEmail).orElse(null);
        if (user == null) {
            return new ReviewEligibilityResponse(false, "NOT_LOGGED_IN");
        }
        if (user.getRole() != null && !"customer".equalsIgnoreCase(user.getRole().getName())) {
            return new ReviewEligibilityResponse(false, "FORBIDDEN_ROLE");
        }
        if (reviewRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            return new ReviewEligibilityResponse(false, "ALREADY_REVIEWED");
        }
        if (reviewRepository.countCompletedPurchases(user.getId(), productId) == 0) {
            return new ReviewEligibilityResponse(false, "NOT_COMPLETED_ORDER");
        }
        return new ReviewEligibilityResponse(true, "OK");
    }

    @Override
    @Transactional
    public ReviewResponse createReview(String userEmail, Long productId, CreateReviewRequest request) {
        Product product = ensureActiveProductExists(productId);
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản"));

        if (user.getRole() != null && !"customer".equalsIgnoreCase(user.getRole().getName())) {
            throw new AccessDeniedException("Chỉ tài khoản khách hàng mới có quyền đánh giá sản phẩm");
        }
        if (reviewRepository.countCompletedPurchases(user.getId(), productId) == 0) {
            throw new AccessDeniedException("Bạn chỉ có thể đánh giá sau khi đặt hàng và được xác nhận đã giao hàng thành công");
        }
        if (reviewRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new ConflictException("Bạn đã đánh giá sản phẩm này rồi");
        }

        Review review = Review.builder()
                .product(product)
                .user(user)
                .rating((byte) request.rating().intValue())
                .content(request.content() == null || request.content().isBlank() ? null : request.content().trim())
                .build();

        try {
            Review saved = reviewRepository.saveAndFlush(review);
            return ReviewResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Trùng lặp đánh giá cho user {} và product {}", user.getId(), productId);
            throw new ConflictException("Bạn đã đánh giá sản phẩm này rồi");
        }
    }

    private Product ensureActiveProductExists(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + productId));
        if (Boolean.FALSE.equals(product.getIsActive())) {
            throw new ResourceNotFoundException("Sản phẩm hiện đang ngừng kinh doanh");
        }
        return product;
    }
}
