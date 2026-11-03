package com.shop.backend.config;

import com.shop.backend.common.model.DiscountType;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.order.OrderRepository;
import com.shop.backend.payment.PaymentRepository;
import com.shop.backend.product.Category;
import com.shop.backend.product.CategoryRepository;
import com.shop.backend.product.Product;
import com.shop.backend.product.ProductImage;
import com.shop.backend.product.ProductImageRepository;
import com.shop.backend.product.ProductRepository;
import com.shop.backend.product.ProductVariant;
import com.shop.backend.product.ProductVariantRepository;
import com.shop.backend.review.ReviewRepository;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import com.shop.backend.voucher.Voucher;
import com.shop.backend.voucher.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevDataSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Environment environment;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private DevDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DevDataSeeder(
                userRepository,
                roleRepository,
                categoryRepository,
                productRepository,
                productVariantRepository,
                productImageRepository,
                voucherRepository,
                orderRepository,
                paymentRepository,
                invoiceRepository,
                reviewRepository,
                passwordEncoder,
                environment,
                transactionManager
        );
    }

    private void stubCleanDatabase() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
        when(userRepository.count()).thenReturn(0L);
        when(orderRepository.count()).thenReturn(0L);
        when(paymentRepository.count()).thenReturn(0L);
        when(invoiceRepository.count()).thenReturn(0L);
        when(categoryRepository.count()).thenReturn(0L);
        when(productRepository.count()).thenReturn(0L);
        when(voucherRepository.count()).thenReturn(0L);
        when(reviewRepository.count()).thenReturn(0L);
    }

    @Test
    @DisplayName("1. Should be active only on local and dev profiles via annotation")
    void shouldBeConfiguredForLocalAndDevProfilesOnly() {
        Profile profileAnnotation = DevDataSeeder.class.getAnnotation(Profile.class);
        assertNotNull(profileAnnotation);
        List<String> profiles = Arrays.asList(profileAnnotation.value());
        assertTrue(profiles.contains("local"));
        assertTrue(profiles.contains("dev"));
        assertFalse(profiles.contains("test"));
        assertFalse(profiles.contains("prod"));
    }

    @Test
    @DisplayName("2. Should skip seeder immediately when app.seed.dev.enabled is false")
    void shouldSkipWhenSeedDisabled() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", false);

        seeder.run(new DefaultApplicationArguments());

        verify(userRepository, never()).count();
        verify(categoryRepository, never()).count();
        verify(productRepository, never()).count();
        verify(orderRepository, never()).count();
        verify(paymentRepository, never()).count();
        verify(invoiceRepository, never()).count();
        verify(voucherRepository, never()).count();
        verify(reviewRepository, never()).count();
    }

    @Test
    @DisplayName("3. Should abort seeder when prod profile is active, even if combined with local/dev")
    void shouldAbortWhenProdProfileIsActive() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local", "prod"});

        seeder.run(new DefaultApplicationArguments());

        verify(userRepository, never()).count();
        verify(orderRepository, never()).count();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("4. Should abort seeder when test profile is active")
    void shouldAbortWhenTestProfileIsActive() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});

        seeder.run(new DefaultApplicationArguments());

        verify(userRepository, never()).count();
        verify(orderRepository, never()).count();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("5. Should skip entire seeder when user records exist even if categories and products are empty")
    void shouldSkipWhenUserDataExistsEvenIfCategoriesEmpty() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
        when(userRepository.count()).thenReturn(3L);

        seeder.run(new DefaultApplicationArguments());

        verify(userRepository).count();
        verify(orderRepository, never()).count();
        verify(categoryRepository, never()).count();
        verify(productRepository, never()).count();
        verify(userRepository, never()).save(any(User.class));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("6. Should skip entire seeder when order records exist even if categories and products are empty")
    void shouldSkipWhenOrderDataExistsEvenIfCategoriesEmpty() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
        when(userRepository.count()).thenReturn(0L);
        when(orderRepository.count()).thenReturn(5L);

        seeder.run(new DefaultApplicationArguments());

        verify(userRepository).count();
        verify(orderRepository).count();
        verify(paymentRepository, never()).count();
        verify(categoryRepository, never()).count();
        verify(productRepository, never()).count();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("7. Should skip entire seeder when payment records exist even if categories and products are empty")
    void shouldSkipWhenPaymentDataExistsEvenIfCategoriesEmpty() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
        when(userRepository.count()).thenReturn(0L);
        when(orderRepository.count()).thenReturn(0L);
        when(paymentRepository.count()).thenReturn(4L);

        seeder.run(new DefaultApplicationArguments());

        verify(userRepository).count();
        verify(orderRepository).count();
        verify(paymentRepository).count();
        verify(invoiceRepository, never()).count();
        verify(categoryRepository, never()).count();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("8. Should skip entire seeder when invoice records exist even if categories and products are empty")
    void shouldSkipWhenInvoiceDataExistsEvenIfCategoriesEmpty() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
        when(userRepository.count()).thenReturn(0L);
        when(orderRepository.count()).thenReturn(0L);
        when(paymentRepository.count()).thenReturn(0L);
        when(invoiceRepository.count()).thenReturn(2L);

        seeder.run(new DefaultApplicationArguments());

        verify(userRepository).count();
        verify(orderRepository).count();
        verify(paymentRepository).count();
        verify(invoiceRepository).count();
        verify(categoryRepository, never()).count();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("9. Should skip entire seeder when category data exists in database")
    void shouldSkipWhenCategoryDataAlreadyExists() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
        when(userRepository.count()).thenReturn(0L);
        when(orderRepository.count()).thenReturn(0L);
        when(paymentRepository.count()).thenReturn(0L);
        when(invoiceRepository.count()).thenReturn(0L);
        when(categoryRepository.count()).thenReturn(10L);

        seeder.run(new DefaultApplicationArguments());

        verify(categoryRepository).count();
        verify(productRepository, never()).count();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("10. Should skip entire seeder when product data exists in database")
    void shouldSkipWhenProductDataAlreadyExists() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
        when(userRepository.count()).thenReturn(0L);
        when(orderRepository.count()).thenReturn(0L);
        when(paymentRepository.count()).thenReturn(0L);
        when(invoiceRepository.count()).thenReturn(0L);
        when(categoryRepository.count()).thenReturn(0L);
        when(productRepository.count()).thenReturn(25L);

        seeder.run(new DefaultApplicationArguments());

        verify(categoryRepository).count();
        verify(productRepository).count();
        verify(voucherRepository, never()).count();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("11. Should skip entire seeder when voucher data exists in database")
    void shouldSkipWhenVoucherDataAlreadyExists() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
        when(userRepository.count()).thenReturn(0L);
        when(orderRepository.count()).thenReturn(0L);
        when(paymentRepository.count()).thenReturn(0L);
        when(invoiceRepository.count()).thenReturn(0L);
        when(categoryRepository.count()).thenReturn(0L);
        when(productRepository.count()).thenReturn(0L);
        when(voucherRepository.count()).thenReturn(2L);

        seeder.run(new DefaultApplicationArguments());

        verify(voucherRepository).count();
        verify(reviewRepository, never()).count();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("12. Should skip entire seeder when review data exists in database")
    void shouldSkipWhenReviewDataAlreadyExists() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
        when(userRepository.count()).thenReturn(0L);
        when(orderRepository.count()).thenReturn(0L);
        when(paymentRepository.count()).thenReturn(0L);
        when(invoiceRepository.count()).thenReturn(0L);
        when(categoryRepository.count()).thenReturn(0L);
        when(productRepository.count()).thenReturn(0L);
        when(voucherRepository.count()).thenReturn(0L);
        when(reviewRepository.count()).thenReturn(7L);

        seeder.run(new DefaultApplicationArguments());

        verify(reviewRepository).count();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("13. Should throw IllegalStateException when admin credentials are missing")
    void shouldThrowExceptionWhenCredentialsMissing() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        ReflectionTestUtils.setField(seeder, "adminEmail", null);
        ReflectionTestUtils.setField(seeder, "adminPassword", null);

        stubCleanDatabase();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seeder.run(new DefaultApplicationArguments()));

        assertTrue(ex.getMessage().contains("SEED_ADMIN_EMAIL or SEED_ADMIN_PASSWORD is missing"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("14. Should throw IllegalStateException when password is less than 6 characters")
    void shouldThrowExceptionWhenPasswordIsLessThan6Characters() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@closet.com");
        ReflectionTestUtils.setField(seeder, "adminPassword", "12345");

        stubCleanDatabase();

        assertThrows(IllegalStateException.class, () -> seeder.run(new DefaultApplicationArguments()));
    }

    @Test
    @DisplayName("15. Should throw IllegalStateException when password is empty")
    void shouldThrowExceptionWhenPasswordIsEmpty() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@closet.com");
        ReflectionTestUtils.setField(seeder, "adminPassword", "");

        stubCleanDatabase();

        assertThrows(IllegalStateException.class, () -> seeder.run(new DefaultApplicationArguments()));
    }

    @Test
    @DisplayName("16. Should throw IllegalStateException when password exceeds 72 bytes")
    void shouldThrowExceptionWhenPasswordTooLong() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@closet.com");
        ReflectionTestUtils.setField(seeder, "adminPassword", "A".repeat(73));

        stubCleanDatabase();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seeder.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("BCrypt limit"));
    }

    @Test
    @DisplayName("17. Should seed admin, categories, products and vouchers atomically on clean database")
    void shouldSeedAllEntitiesOnCleanDatabase() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@closet.com");
        ReflectionTestUtils.setField(seeder, "adminPassword", "Admin@123456");

        Role adminRole = Role.builder().id(1L).name("admin").build();
        Category mockCategory = Category.builder().id(10L).name("Mock Category").slug("mock-cat").build();

        stubCleanDatabase();

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(userRepository.existsByEmail("admin@closet.com")).thenReturn(false);
        when(roleRepository.findByName("admin")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("Admin@123456")).thenReturn("hashedPassword123");
        when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
        when(categoryRepository.findBySlug(anyString())).thenReturn(Optional.of(mockCategory));
        when(productRepository.existsBySlug(anyString())).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(voucherRepository.existsByCode(anyString())).thenReturn(false);

        seeder.run(new DefaultApplicationArguments());

        verify(transactionManager).getTransaction(any());
        verify(transactionManager).commit(transactionStatus);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("admin@closet.com", savedUser.getEmail());
        assertEquals("hashedPassword123", savedUser.getPasswordHash());
        assertEquals("admin", savedUser.getRole().getName());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());

        verify(categoryRepository, times(13)).save(any(Category.class));
        verify(productRepository, times(23)).save(any(Product.class));
        verify(voucherRepository, times(2)).save(any(Voucher.class));
        verify(productImageRepository, times(23)).save(any(ProductImage.class));
    }

    @Test
    @DisplayName("18. Should rollback entire transaction when exception occurs midway during seed")
    void shouldRollbackTransactionWhenExceptionOccursDuringSeed() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@closet.com");
        ReflectionTestUtils.setField(seeder, "adminPassword", "Admin@123456");

        Role adminRole = Role.builder().id(1L).name("admin").build();
        Category mockCategory = Category.builder().id(10L).name("Mock Category").slug("mock-cat").build();

        stubCleanDatabase();

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(userRepository.existsByEmail("admin@closet.com")).thenReturn(false);
        when(roleRepository.findByName("admin")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("Admin@123456")).thenReturn("hashedPassword123");
        when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
        when(categoryRepository.findBySlug(anyString())).thenReturn(Optional.of(mockCategory));
        when(productRepository.existsBySlug(anyString())).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Simulate database exception during voucher seed at the end of the batch
        doThrow(new RuntimeException("Simulated Database Error during Voucher Seed"))
                .when(voucherRepository).save(any(Voucher.class));

        assertThrows(RuntimeException.class, () -> seeder.run(new DefaultApplicationArguments()));

        verify(transactionManager).getTransaction(any());
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    @DisplayName("19. Should fallback to uppercase ADMIN role when lowercase admin is not found")
    void shouldFallbackToUppercaseAdminRole() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@closet.com");
        ReflectionTestUtils.setField(seeder, "adminPassword", "Admin@123456");

        Role uppercaseAdminRole = Role.builder().id(1L).name("ADMIN").build();

        stubCleanDatabase();

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(userRepository.existsByEmail("admin@closet.com")).thenReturn(false);
        when(roleRepository.findByName("admin")).thenReturn(Optional.empty());
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(uppercaseAdminRole));
        when(passwordEncoder.encode("Admin@123456")).thenReturn("hashedPassword123");

        seeder.run(new DefaultApplicationArguments());

        verify(transactionManager).commit(transactionStatus);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("ADMIN", userCaptor.getValue().getRole().getName());
    }

    @Test
    @DisplayName("20. Should throw IllegalStateException when neither admin nor ADMIN role exists")
    void shouldThrowExceptionWhenAdminRoleNotFound() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@closet.com");
        ReflectionTestUtils.setField(seeder, "adminPassword", "Admin@123456");

        stubCleanDatabase();

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(userRepository.existsByEmail("admin@closet.com")).thenReturn(false);
        when(roleRepository.findByName("admin")).thenReturn(Optional.empty());
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seeder.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("Role 'admin' not found"));

        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    @DisplayName("21. Should verify seeded sample vouchers have correct configurations")
    void shouldSeedValidSampleVouchers() {
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@closet.com");
        ReflectionTestUtils.setField(seeder, "adminPassword", "Admin@123456");

        stubCleanDatabase();

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(userRepository.existsByEmail("admin@closet.com")).thenReturn(true);
        when(categoryRepository.existsBySlug(anyString())).thenReturn(true);
        when(productRepository.existsBySlug(anyString())).thenReturn(true);
        when(voucherRepository.existsByCode(anyString())).thenReturn(false);

        seeder.run(new DefaultApplicationArguments());

        verify(transactionManager).commit(transactionStatus);

        ArgumentCaptor<Voucher> voucherCaptor = ArgumentCaptor.forClass(Voucher.class);
        verify(voucherRepository, times(2)).save(voucherCaptor.capture());
        List<Voucher> savedVouchers = voucherCaptor.getAllValues();

        Voucher welcomeVoucher = savedVouchers.stream()
                .filter(v -> "WELCOME10".equals(v.getCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(welcomeVoucher);
        assertEquals(DiscountType.PERCENT, welcomeVoucher.getDiscountType());
        assertEquals(BigDecimal.valueOf(10), welcomeVoucher.getDiscountValue());
        assertEquals(BigDecimal.valueOf(200000), welcomeVoucher.getMinOrder());
        assertEquals(BigDecimal.valueOf(50000), welcomeVoucher.getMaxDiscount());
        assertTrue(welcomeVoucher.getIsActive());

        Voucher atelierVoucher = savedVouchers.stream()
                .filter(v -> "ATELIER50".equals(v.getCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(atelierVoucher);
        assertEquals(DiscountType.AMOUNT, atelierVoucher.getDiscountType());
        assertEquals(BigDecimal.valueOf(50000), atelierVoucher.getDiscountValue());
        assertEquals(BigDecimal.valueOf(300000), atelierVoucher.getMinOrder());
        assertTrue(atelierVoucher.getIsActive());
    }
}
