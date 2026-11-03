package com.shop.backend.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderDetail;
import com.shop.backend.order.OrderDetailRepository;
import com.shop.backend.order.OrderRepository;
import com.shop.backend.order.OrderStatus;
import com.shop.backend.product.Category;
import com.shop.backend.product.CategoryRepository;
import com.shop.backend.product.Product;
import com.shop.backend.product.ProductRepository;
import com.shop.backend.product.ProductVariant;
import com.shop.backend.product.ProductVariantRepository;
import com.shop.backend.review.dto.CreateReviewRequest;
import com.shop.backend.security.JwtService;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ReviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private User customerA;
    private User customerB;
    private User adminUser;
    private User staffUser;
    private Product testProduct;
    private ProductVariant testVariant;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(Role.builder().name("customer").build()));
        Role adminRole = roleRepository.findByName("admin")
                .orElseGet(() -> roleRepository.save(Role.builder().name("admin").build()));
        Role staffRole = roleRepository.findByName("staff")
                .orElseGet(() -> roleRepository.save(Role.builder().name("staff").build()));

        String uid = UUID.randomUUID().toString().substring(0, 8);

        customerA = userRepository.save(User.builder()
                .name("Customer A")
                .email("cust.a." + uid + "@shop.test")
                .passwordHash("hashed_pwd")
                .role(customerRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        customerB = userRepository.save(User.builder()
                .name("Customer B")
                .email("cust.b." + uid + "@shop.test")
                .passwordHash("hashed_pwd")
                .role(customerRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        adminUser = userRepository.save(User.builder()
                .name("Admin User")
                .email("admin." + uid + "@shop.test")
                .passwordHash("hashed_pwd")
                .role(adminRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        staffUser = userRepository.save(User.builder()
                .name("Staff User")
                .email("staff." + uid + "@shop.test")
                .passwordHash("hashed_pwd")
                .role(staffRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Category " + uid)
                .slug("cat-" + uid)
                .isActive(true)
                .build());

        testProduct = productRepository.save(Product.builder()
                .name("Áo Polo Review Test " + uid)
                .slug("ao-polo-review-" + uid)
                .category(category)
                .basePrice(new BigDecimal("250000.00"))
                .isActive(true)
                .build());

        testVariant = productVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku("SKU-REV-" + uid)
                .color("Đen")
                .size("L")
                .stock(100)
                .price(new BigDecimal("250000.00"))
                .build());
    }

    private Order createOrder(User user, OrderStatus status) {
        Order order = orderRepository.save(Order.builder()
                .user(user)
                .status(status)
                .subtotal(new BigDecimal("250000.00"))
                .totalAmount(new BigDecimal("250000.00"))
                .shippingFee(BigDecimal.ZERO)
                .receiverName(user.getName())
                .receiverPhone("0987654321")
                .province("Hà Nội")
                .district("Cầu Giấy")
                .ward("Dịch Vọng")
                .addressLine("123 Test Street")
                .build());

        orderDetailRepository.save(OrderDetail.builder()
                .order(order)
                .variant(testVariant)
                .productName(testProduct.getName())
                .variantColor(testVariant.getColor())
                .variantSize(testVariant.getSize())
                .quantity(1)
                .unitPrice(testVariant.getPrice())
                .discountAmount(BigDecimal.ZERO)
                .build());

        return order;
    }

    @Test
    @DisplayName("1. Khách hàng đã mua sản phẩm trong đơn COMPLETED -> Tạo review thành công (201 Created)")
    void customerWithCompletedOrder_CanCreateReview() throws Exception {
        createOrder(customerA, OrderStatus.COMPLETED);
        String token = jwtService.generateToken(customerA);

        CreateReviewRequest request = new CreateReviewRequest(5, "Chất vải dày dặn, mặc rất ưng ý!");

        mockMvc.perform(post("/api/products/" + testProduct.getId() + "/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(testProduct.getId()))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.content").value("Chất vải dày dặn, mặc rất ưng ý!"))
                .andExpect(jsonPath("$.userName").value("Customer A"));
    }

    @Test
    @DisplayName("2. Khách hàng có đơn nhưng chưa COMPLETED (SHIPPING) -> Bị từ chối 403 Forbidden")
    void customerWithShippingOrder_CannotCreateReview() throws Exception {
        createOrder(customerA, OrderStatus.SHIPPING);
        String token = jwtService.generateToken(customerA);

        CreateReviewRequest request = new CreateReviewRequest(4, "Chưa nhận hàng thử review");

        mockMvc.perform(post("/api/products/" + testProduct.getId() + "/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền thực hiện thao tác này"));
    }

    @Test
    @DisplayName("3. Sản phẩm đã mua bởi User khác, User hiện tại chưa mua -> Bị từ chối 403 Forbidden")
    void customerB_CannotReview_WhenOnlyCustomerAPurchased() throws Exception {
        createOrder(customerA, OrderStatus.COMPLETED);
        String tokenB = jwtService.generateToken(customerB);

        CreateReviewRequest request = new CreateReviewRequest(5, "Review dạo");

        mockMvc.perform(post("/api/products/" + testProduct.getId() + "/reviews")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền thực hiện thao tác này"));
    }

    @Test
    @DisplayName("4. User chưa đăng nhập gọi POST review -> Bị từ chối 401 Unauthorized")
    void unauthenticatedUser_CannotCreateReview() throws Exception {
        CreateReviewRequest request = new CreateReviewRequest(5, "Chưa login");

        mockMvc.perform(post("/api/products/" + testProduct.getId() + "/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa xác thực danh tính"));
    }

    @Test
    @DisplayName("5. Tài khoản ADMIN không được tạo review -> Bị từ chối 403 Forbidden")
    void adminUser_CannotCreateReview() throws Exception {
        createOrder(adminUser, OrderStatus.COMPLETED);
        String adminToken = jwtService.generateToken(adminUser);

        CreateReviewRequest request = new CreateReviewRequest(5, "Admin đánh giá");

        mockMvc.perform(post("/api/products/" + testProduct.getId() + "/reviews")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5b. Tài khoản STAFF không được tạo review -> Bị từ chối 403 Forbidden")
    void staffUser_CannotCreateReview() throws Exception {
        createOrder(staffUser, OrderStatus.COMPLETED);
        String staffToken = jwtService.generateToken(staffUser);

        CreateReviewRequest request = new CreateReviewRequest(5, "Staff đánh giá");

        mockMvc.perform(post("/api/products/" + testProduct.getId() + "/reviews")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("6. Cùng một user/product tạo review lần 2 -> Bị từ chối 409 Conflict")
    void duplicateReview_ThrowsConflict() throws Exception {
        createOrder(customerA, OrderStatus.COMPLETED);
        String token = jwtService.generateToken(customerA);

        CreateReviewRequest request1 = new CreateReviewRequest(5, "Đánh giá lần 1");
        mockMvc.perform(post("/api/products/" + testProduct.getId() + "/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        CreateReviewRequest request2 = new CreateReviewRequest(4, "Đánh giá lần 2 cố tình tạo trùng");
        mockMvc.perform(post("/api/products/" + testProduct.getId() + "/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Bạn đã đánh giá sản phẩm này rồi"));
    }

    @Test
    @DisplayName("7. Product không tồn tại -> Trả về 404 Not Found")
    void nonExistentProduct_ThrowsNotFound() throws Exception {
        createOrder(customerA, OrderStatus.COMPLETED);
        String token = jwtService.generateToken(customerA);

        CreateReviewRequest request = new CreateReviewRequest(5, "Review sản phẩm 999999");

        mockMvc.perform(post("/api/products/999999/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy sản phẩm với id: 999999"));
    }

    @Test
    @DisplayName("8. GET list và summary là public, tính đúng điểm trung bình")
    void publicEndpoints_WorkCorrectly() throws Exception {
        createOrder(customerA, OrderStatus.COMPLETED);
        String tokenA = jwtService.generateToken(customerA);

        mockMvc.perform(post("/api/products/" + testProduct.getId() + "/reviews")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(5, "Tuyệt vời"))))
                .andExpect(status().isCreated());

        // GET list mà không cần token
        mockMvc.perform(get("/api/products/" + testProduct.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.content[0].content").value("Tuyệt vời"));

        // GET summary mà không cần token
        mockMvc.perform(get("/api/products/" + testProduct.getId() + "/reviews/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.average").value(5.0))
                .andExpect(jsonPath("$.distribution.5").value(1));

        // GET eligibility không cần token -> trả về canReview = false, reason = NOT_LOGGED_IN
        mockMvc.perform(get("/api/products/" + testProduct.getId() + "/reviews/eligibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canReview").value(false))
                .andExpect(jsonPath("$.reason").value("NOT_LOGGED_IN"));

        // GET eligibility với token customerA (đã review rồi) -> canReview = false, reason = ALREADY_REVIEWED
        mockMvc.perform(get("/api/products/" + testProduct.getId() + "/reviews/eligibility")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canReview").value(false))
                .andExpect(jsonPath("$.reason").value("ALREADY_REVIEWED"));
    }
}
