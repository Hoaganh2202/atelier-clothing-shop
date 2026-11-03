package com.shop.backend.review;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.exception.ConflictException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.product.Product;
import com.shop.backend.product.ProductRepository;
import com.shop.backend.review.dto.CreateReviewRequest;
import com.shop.backend.review.dto.ReviewEligibilityResponse;
import com.shop.backend.review.dto.ReviewResponse;
import com.shop.backend.review.dto.ReviewSummaryResponse;
import com.shop.backend.user.Role;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private Product activeProduct;
    private Product inactiveProduct;
    private User customerUser;
    private User adminUser;
    private Role customerRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        customerRole = Role.builder().id(1L).name("customer").build();
        adminRole = Role.builder().id(2L).name("admin").build();

        activeProduct = Product.builder()
                .id(100L)
                .name("Áo thun Cotton Basic")
                .slug("ao-thun-cotton-basic")
                .isActive(true)
                .build();

        inactiveProduct = Product.builder()
                .id(200L)
                .name("Áo khoác cũ")
                .slug("ao-khoac-cu")
                .isActive(false)
                .build();

        customerUser = User.builder()
                .id(10L)
                .name("Nguyễn Văn A")
                .email("customer@shop.com")
                .role(customerRole)
                .build();

        adminUser = User.builder()
                .id(20L)
                .name("Quản Trị Viên")
                .email("admin@shop.com")
                .role(adminRole)
                .build();
    }

    @Test
    @DisplayName("Tạo đánh giá thành công khi khách hàng đã mua sản phẩm trong đơn COMPLETED")
    void createReview_Success_WhenCustomerCompletedPurchase() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(activeProduct));
        when(userRepository.findByEmail("customer@shop.com")).thenReturn(Optional.of(customerUser));
        when(reviewRepository.countCompletedPurchases(10L, 100L)).thenReturn(1L);
        when(reviewRepository.existsByUserIdAndProductId(10L, 100L)).thenReturn(false);

        Review savedReview = Review.builder()
                .id(1L)
                .product(activeProduct)
                .user(customerUser)
                .rating((byte) 5)
                .content("Sản phẩm rất đẹp và thoáng mát!")
                .createdAt(LocalDateTime.now())
                .build();

        when(reviewRepository.saveAndFlush(any(Review.class))).thenReturn(savedReview);

        CreateReviewRequest request = new CreateReviewRequest(5, "Sản phẩm rất đẹp và thoáng mát!");
        ReviewResponse response = reviewService.createReview("customer@shop.com", 100L, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.content()).isEqualTo("Sản phẩm rất đẹp và thoáng mát!");
        assertThat(response.userName()).isEqualTo("Nguyễn Văn A");
        verify(reviewRepository).saveAndFlush(any(Review.class));
    }

    @Test
    @DisplayName("Ném AccessDeniedException khi khách hàng chưa mua sản phẩm hoặc đơn chưa COMPLETED")
    void createReview_ThrowsAccessDenied_WhenUserHasNotPurchased() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(activeProduct));
        when(userRepository.findByEmail("customer@shop.com")).thenReturn(Optional.of(customerUser));
        when(reviewRepository.countCompletedPurchases(10L, 100L)).thenReturn(0L);

        CreateReviewRequest request = new CreateReviewRequest(5, "Hàng đẹp");

        assertThatThrownBy(() -> reviewService.createReview("customer@shop.com", 100L, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn chỉ có thể đánh giá sau khi đặt hàng và được xác nhận đã giao hàng thành công");
    }

    @Test
    @DisplayName("Ném ConflictException khi khách hàng đã đánh giá sản phẩm này trước đó")
    void createReview_ThrowsConflict_WhenAlreadyReviewed() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(activeProduct));
        when(userRepository.findByEmail("customer@shop.com")).thenReturn(Optional.of(customerUser));
        when(reviewRepository.countCompletedPurchases(10L, 100L)).thenReturn(2L);
        when(reviewRepository.existsByUserIdAndProductId(10L, 100L)).thenReturn(true);

        CreateReviewRequest request = new CreateReviewRequest(4, "Đánh giá lần 2");

        assertThatThrownBy(() -> reviewService.createReview("customer@shop.com", 100L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Bạn đã đánh giá sản phẩm này rồi");
    }

    @Test
    @DisplayName("Ném ConflictException khi xảy ra race condition vi phạm unique constraint DB")
    void createReview_ThrowsConflict_WhenRaceConditionOccurs() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(activeProduct));
        when(userRepository.findByEmail("customer@shop.com")).thenReturn(Optional.of(customerUser));
        when(reviewRepository.countCompletedPurchases(10L, 100L)).thenReturn(1L);
        when(reviewRepository.existsByUserIdAndProductId(10L, 100L)).thenReturn(false);
        when(reviewRepository.saveAndFlush(any(Review.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        CreateReviewRequest request = new CreateReviewRequest(5, "Đánh giá đồng thời");

        assertThatThrownBy(() -> reviewService.createReview("customer@shop.com", 100L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Bạn đã đánh giá sản phẩm này rồi");
    }

    @Test
    @DisplayName("Ném AccessDeniedException khi tài khoản ADMIN hoặc STAFF cố tình đánh giá")
    void createReview_ThrowsAccessDenied_WhenUserIsAdminOrStaff() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(activeProduct));
        when(userRepository.findByEmail("admin@shop.com")).thenReturn(Optional.of(adminUser));

        CreateReviewRequest request = new CreateReviewRequest(5, "Admin đánh giá");

        assertThatThrownBy(() -> reviewService.createReview("admin@shop.com", 100L, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Chỉ tài khoản khách hàng mới có quyền đánh giá sản phẩm");
    }

    @Test
    @DisplayName("Ném ResourceNotFoundException khi sản phẩm không tồn tại")
    void createReview_ThrowsResourceNotFound_WhenProductNotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        CreateReviewRequest request = new CreateReviewRequest(5, "Không có sản phẩm");

        assertThatThrownBy(() -> reviewService.createReview("customer@shop.com", 999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy sản phẩm");
    }

    @Test
    @DisplayName("Ném ResourceNotFoundException khi sản phẩm đã ngừng kinh doanh")
    void createReview_ThrowsResourceNotFound_WhenProductNotActive() {
        when(productRepository.findById(200L)).thenReturn(Optional.of(inactiveProduct));

        CreateReviewRequest request = new CreateReviewRequest(5, "Sản phẩm ngưng bán");

        assertThatThrownBy(() -> reviewService.createReview("customer@shop.com", 200L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Sản phẩm hiện đang ngừng kinh doanh");
    }

    @Test
    @DisplayName("Eligibility trả về NOT_LOGGED_IN khi người dùng chưa đăng nhập")
    void checkEligibility_ReturnsNotLoggedIn_WhenEmailNull() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(activeProduct));

        ReviewEligibilityResponse result = reviewService.checkEligibility(null, 100L);

        assertThat(result.canReview()).isFalse();
        assertThat(result.reason()).isEqualTo("NOT_LOGGED_IN");
    }

    @Test
    @DisplayName("Eligibility trả về FORBIDDEN_ROLE khi người dùng là Admin")
    void checkEligibility_ReturnsForbiddenRole_WhenAdmin() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(activeProduct));
        when(userRepository.findByEmail("admin@shop.com")).thenReturn(Optional.of(adminUser));

        ReviewEligibilityResponse result = reviewService.checkEligibility("admin@shop.com", 100L);

        assertThat(result.canReview()).isFalse();
        assertThat(result.reason()).isEqualTo("FORBIDDEN_ROLE");
    }

    @Test
    @DisplayName("Eligibility trả về ALREADY_REVIEWED khi khách đã đánh giá")
    void checkEligibility_ReturnsAlreadyReviewed() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(activeProduct));
        when(userRepository.findByEmail("customer@shop.com")).thenReturn(Optional.of(customerUser));
        when(reviewRepository.existsByUserIdAndProductId(10L, 100L)).thenReturn(true);

        ReviewEligibilityResponse result = reviewService.checkEligibility("customer@shop.com", 100L);

        assertThat(result.canReview()).isFalse();
        assertThat(result.reason()).isEqualTo("ALREADY_REVIEWED");
    }

    @Test
    @DisplayName("Eligibility trả về NOT_COMPLETED_ORDER khi khách chưa mua đơn COMPLETED")
    void checkEligibility_ReturnsNotCompletedOrder() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(activeProduct));
        when(userRepository.findByEmail("customer@shop.com")).thenReturn(Optional.of(customerUser));
        when(reviewRepository.existsByUserIdAndProductId(10L, 100L)).thenReturn(false);
        when(reviewRepository.countCompletedPurchases(10L, 100L)).thenReturn(0L);

        ReviewEligibilityResponse result = reviewService.checkEligibility("customer@shop.com", 100L);

        assertThat(result.canReview()).isFalse();
        assertThat(result.reason()).isEqualTo("NOT_COMPLETED_ORDER");
    }

    @Test
    @DisplayName("Eligibility trả về OK khi khách hàng đủ điều kiện đánh giá")
    void checkEligibility_ReturnsOk_WhenEligible() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(activeProduct));
        when(userRepository.findByEmail("customer@shop.com")).thenReturn(Optional.of(customerUser));
        when(reviewRepository.existsByUserIdAndProductId(10L, 100L)).thenReturn(false);
        when(reviewRepository.countCompletedPurchases(10L, 100L)).thenReturn(1L);

        ReviewEligibilityResponse result = reviewService.checkEligibility("customer@shop.com", 100L);

        assertThat(result.canReview()).isTrue();
        assertThat(result.reason()).isEqualTo("OK");
    }

    @Test
    @DisplayName("Tính toán tổng quan đánh giá: điểm trung bình và phân bố sao chính xác")
    void getReviewSummary_CalculatesAverageAndDistribution() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(activeProduct));

        List<Object[]> ratingStats = List.of(
                new Object[]{5, 3L},
                new Object[]{4, 1L}
        );
        when(reviewRepository.countByRatingGrouped(100L)).thenReturn(ratingStats);

        ReviewSummaryResponse summary = reviewService.getReviewSummary(100L);

        assertThat(summary.count()).isEqualTo(4L);
        // (5*3 + 4*1) / 4 = 19 / 4 = 4.75 -> round 1 decimal = 4.8
        assertThat(summary.average()).isEqualTo(4.8);
        assertThat(summary.distribution().get(5)).isEqualTo(3L);
        assertThat(summary.distribution().get(4)).isEqualTo(1L);
        assertThat(summary.distribution().get(3)).isEqualTo(0L);
    }

    @Test
    @DisplayName("Lấy danh sách đánh giá phân trang thành công")
    void listReviews_ReturnsPaginatedReviews() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(activeProduct));

        Review review1 = Review.builder()
                .id(1L)
                .product(activeProduct)
                .user(customerUser)
                .rating((byte) 5)
                .content("Rất tốt")
                .createdAt(LocalDateTime.now())
                .build();

        when(reviewRepository.findByProductId(eq(100L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review1)));

        PageResponse<ReviewResponse> result = reviewService.listReviews(100L, 0, 5);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).content()).isEqualTo("Rất tốt");
    }
}
