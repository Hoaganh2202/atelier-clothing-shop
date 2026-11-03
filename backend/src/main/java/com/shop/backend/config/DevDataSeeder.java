package com.shop.backend.config;

import com.shop.backend.common.model.DataSourceType;
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
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Idempotent seeder active only in local and dev profiles for clean database initialization.
 */
@Slf4j
@Component
@Profile({"local", "dev"})
public class DevDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final VoucherRepository voucherRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final PlatformTransactionManager transactionManager;
    private final TransactionTemplate transactionTemplate;
    private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    @Value("${app.upload.dir:uploads/products}")
    private String uploadDirProperty = "uploads/products";

    @Value("${app.seed.dev.enabled:false}")
    private boolean seedEnabled;

    @Value("${app.seed.dev.admin-email:#{null}}")
    private String adminEmail;

    @Value("${app.seed.dev.admin-password:#{null}}")
    private String adminPassword;

    public DevDataSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            ProductImageRepository productImageRepository,
            VoucherRepository voucherRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            InvoiceRepository invoiceRepository,
            ReviewRepository reviewRepository,
            PasswordEncoder passwordEncoder,
            Environment environment,
            PlatformTransactionManager transactionManager
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productImageRepository = productImageRepository;
        this.voucherRepository = voucherRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.reviewRepository = reviewRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.transactionManager = transactionManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.debug("Dev seeder is disabled (app.seed.dev.enabled=false). Skipping.");
            return;
        }

        if (isDisallowedProfileActive()) {
            log.warn("Dev seeder: Disallowed profile active (prod or test). Skipping seed entirely.");
            return;
        }

        if (hasExistingBusinessData()) {
            return;
        }

        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            throw new IllegalStateException("Dev seeder is enabled (SEED_DEV_ENABLED=true) but SEED_ADMIN_EMAIL or SEED_ADMIN_PASSWORD is missing. Please provide both credentials.");
        }

        byte[] passwordBytes = adminPassword.getBytes(StandardCharsets.UTF_8);
        if (adminPassword.length() < 6 || passwordBytes.length > 72) {
            throw new IllegalStateException("SEED_ADMIN_PASSWORD must be between 6 and 72 bytes (BCrypt limit).");
        }

        copyDemoImages();

        transactionTemplate.executeWithoutResult(status -> {
            seedAdminUser();
            seedSampleCategories();
            seedSampleProducts();
            seedSampleVouchers();
        });
    }

    private boolean isDisallowedProfileActive() {
        if (environment == null) {
            return false;
        }
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        return activeProfiles.contains("prod") || activeProfiles.contains("test");
    }

    private boolean hasExistingBusinessData() {
        long userCount = userRepository.count();
        if (userCount > 0) {
            log.warn("Dev seeder: Existing user data detected (users: {}). Skipping seed to protect database state.", userCount);
            return true;
        }

        long orderCount = orderRepository.count();
        if (orderCount > 0) {
            log.warn("Dev seeder: Existing order data detected (orders: {}). Skipping seed to protect database state.", orderCount);
            return true;
        }

        long paymentCount = paymentRepository.count();
        if (paymentCount > 0) {
            log.warn("Dev seeder: Existing payment data detected (payments: {}). Skipping seed to protect database state.", paymentCount);
            return true;
        }

        long invoiceCount = invoiceRepository.count();
        if (invoiceCount > 0) {
            log.warn("Dev seeder: Existing invoice data detected (invoices: {}). Skipping seed to protect database state.", invoiceCount);
            return true;
        }

        long categoryCount = categoryRepository.count();
        if (categoryCount > 0) {
            log.warn("Dev seeder: Existing category data detected (categories: {}). Skipping seed to protect database state.", categoryCount);
            return true;
        }

        long productCount = productRepository.count();
        if (productCount > 0) {
            log.warn("Dev seeder: Existing product data detected (products: {}). Skipping seed to protect database state.", productCount);
            return true;
        }

        long voucherCount = voucherRepository.count();
        if (voucherCount > 0) {
            log.warn("Dev seeder: Existing voucher data detected (vouchers: {}). Skipping seed to protect database state.", voucherCount);
            return true;
        }

        long reviewCount = reviewRepository.count();
        if (reviewCount > 0) {
            log.warn("Dev seeder: Existing review data detected (reviews: {}). Skipping seed to protect database state.", reviewCount);
            return true;
        }

        return false;
    }

    private void copyDemoImages() {
        if (!StringUtils.hasText(uploadDirProperty)) {
            return;
        }
        try {
            Path targetDir = Paths.get(uploadDirProperty).toAbsolutePath().normalize();
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            Resource[] resources = resourcePatternResolver.getResources("classpath:demo-images/*.*");
            int copiedCount = 0;
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || filename.isBlank()) {
                    continue;
                }
                Path targetFile = targetDir.resolve(filename);
                if (!Files.exists(targetFile)) {
                    try (InputStream is = resource.getInputStream()) {
                        Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        copiedCount++;
                    }
                }
            }
            if (copiedCount > 0) {
                log.info("Dev seeder: copied {} demo image(s) to {}", copiedCount, targetDir);
            } else {
                log.debug("Dev seeder: all demo images already exist in {}", targetDir);
            }
        } catch (IOException e) {
            log.error("Dev seeder: failed to copy demo images: {}", e.getMessage(), e);
        }
    }

    private void seedAdminUser() {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Dev seeder: admin user already exists, skipping.");
            return;
        }

        Role adminRole = roleRepository.findByName("admin")
                .or(() -> roleRepository.findByName("ADMIN"))
                .orElseThrow(() -> new IllegalStateException("Role 'admin' not found in database. Run Flyway migrations first."));

        User admin = User.builder()
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .name("Admin")
                .role(adminRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build();

        userRepository.save(admin);
        log.info("Dev seeder: created admin user with email={}.", adminEmail);
    }

    private void seedSampleCategories() {
        List<String[]> parentCategories = List.of(
                new String[]{"Áo Nam", "ao-nam"},
                new String[]{"Quần Nam", "quan-nam"},
                new String[]{"Áo Nữ", "ao-nu"},
                new String[]{"Quần Nữ", "quan-nu"},
                new String[]{"Phụ Kiện", "phu-kien"}
        );

        for (String[] catData : parentCategories) {
            String name = catData[0];
            String slug = catData[1];
            if (!categoryRepository.existsBySlug(slug)) {
                categoryRepository.save(Category.builder()
                        .name(name)
                        .slug(slug)
                        .isActive(true)
                        .build());
                log.info("Dev seeder: created parent category '{}'.", name);
            }
        }

        List<String[]> childCategories = List.of(
                new String[]{"Áo Thun Nam", "ao-thun-nam", "ao-nam"},
                new String[]{"Áo Sơ Mi Nam", "ao-so-mi-nam", "ao-nam"},
                new String[]{"Quần Jean Nam", "quan-jean-nam", "quan-nam"},
                new String[]{"Quần Kaki Nam", "quan-kaki-nam", "quan-nam"},
                new String[]{"Áo Sơ Mi Nữ", "ao-so-mi-nu", "ao-nu"},
                new String[]{"Áo Kiểu Nữ", "ao-kieu-nu", "ao-nu"},
                new String[]{"Đầm & Váy", "dam-va-vay", "quan-nu"},
                new String[]{"Nón & Mũ", "non-mu", "phu-kien"}
        );

        for (String[] catData : childCategories) {
            String name = catData[0];
            String slug = catData[1];
            String parentSlug = catData[2];

            if (!categoryRepository.existsBySlug(slug)) {
                Category parent = categoryRepository.findBySlug(parentSlug).orElse(null);
                if (parent == null) {
                    log.warn("Dev seeder: parent category '{}' not found, skipping child '{}'.", parentSlug, name);
                    continue;
                }
                categoryRepository.save(Category.builder()
                        .name(name)
                        .slug(slug)
                        .parent(parent)
                        .isActive(true)
                        .build());
                log.info("Dev seeder: created child category '{}' under '{}'.", name, parentSlug);
            }
        }
    }

    private record VariantSeed(String sku, String size, String color, int stock, BigDecimal price) {}

    private record ProductSeed(
            String name,
            String slug,
            String categorySlug,
            BigDecimal basePrice,
            String description,
            boolean isActive,
            List<VariantSeed> variants,
            String imageUrl
    ) {}

    private void seedSampleProducts() {
        List<ProductSeed> sampleProducts = List.of(
                // 1
                new ProductSeed(
                        "Áo thun cotton basic form rộng",
                        "ao-thun-cotton-basic-form-rong",
                        "ao-thun-nam",
                        new BigDecimal("199000"),
                        "Áo thun cotton 100% thoáng mát, thấm hút mồ hôi cực tốt, thích hợp mặc hàng ngày.",
                        true,
                        List.of(
                                new VariantSeed("ATN-01-S-BLK", "S", "Đen", 50, null),
                                new VariantSeed("ATN-01-M-BLK", "M", "Đen", 40, null),
                                new VariantSeed("ATN-01-L-WHT", "L", "Trắng", 30, new BigDecimal("219000")),
                                new VariantSeed("ATN-01-XL-BEI", "XL", "Be", 15, new BigDecimal("219000"))
                        ),
                        "/uploads/products/demo-ao-thun-basic.jpg"
                ),
                // 2
                new ProductSeed(
                        "Áo thun polo nam phối bo cổ",
                        "ao-thun-polo-nam-phoi-bo-co",
                        "ao-thun-nam",
                        new BigDecimal("299000"),
                        "Polo nam chất liệu cá sấu CVC cao cấp, giữ phom tốt, bo cổ dệt tinh tế.",
                        true,
                        List.of(
                                new VariantSeed("APN-02-M-NVY", "M", "Xanh Navy", 35, null),
                                new VariantSeed("APN-02-L-NVY", "L", "Xanh Navy", 20, null),
                                new VariantSeed("APN-02-XL-WHT", "XL", "Trắng", 10, new BigDecimal("319000"))
                        ),
                        "/uploads/products/demo-polo-nam.jpg"
                ),
                // 3
                new ProductSeed(
                        "Áo sơ mi nam oxford dài tay",
                        "ao-so-mi-nam-oxford-dai-tay",
                        "ao-so-mi-nam",
                        new BigDecimal("380000"),
                        "Sơ mi oxford lịch lãm, bề mặt vải dệt tổ ong dày dặn, chống nhăn tự nhiên.",
                        true,
                        List.of(
                                new VariantSeed("SMN-03-M-WHT", "M", "Trắng", 25, null),
                                new VariantSeed("SMN-03-L-BLU", "L", "Xanh Nhạt", 18, null),
                                new VariantSeed("SMN-03-XL-GRY", "XL", "Xám", 12, new BigDecimal("399000"))
                        ),
                        "/uploads/products/demo-somi-oxford.jpg"
                ),
                // 4
                new ProductSeed(
                        "Áo sơ mi lụa cổ cuba nam",
                        "ao-so-mi-lua-co-cuba-nam",
                        "ao-so-mi-nam",
                        new BigDecimal("350000"),
                        "Áo sơ mi lụa tơ tằm nhân tạo mát rượi, cổ cuba phóng khoáng phong cách resort.",
                        true,
                        List.of(
                                new VariantSeed("SMN-04-S-BLK", "S", "Đen", 15, null),
                                new VariantSeed("SMN-04-M-BEI", "M", "Be", 20, null),
                                new VariantSeed("SMN-04-L-BEI", "L", "Be", 25, null)
                        ),
                        "/uploads/products/demo-somi-cuba.jpg"
                ),
                // 5: OUT OF STOCK PRODUCT (all variants stock = 0)
                new ProductSeed(
                        "Áo thun nam graphic print retro",
                        "ao-thun-nam-graphic-print-retro",
                        "ao-thun-nam",
                        new BigDecimal("250000"),
                        "Áo thun in hình phong cách vintage 90s, chất vải dệt thoi mềm mát (Tạm thời hết hàng).",
                        true,
                        List.of(
                                new VariantSeed("ATG-22-M-BLK", "M", "Đen", 0, null),
                                new VariantSeed("ATG-22-L-WHT", "L", "Trắng", 0, null),
                                new VariantSeed("ATG-22-XL-GRY", "XL", "Xám", 0, new BigDecimal("270000"))
                        ),
                        "/uploads/products/demo-ao-thun-retro.jpg"
                ),
                // 6
                new ProductSeed(
                        "Áo khoác gió thể thao nam chống nước",
                        "ao-khoac-gio-the-thao-nam-chong-nuoc",
                        "ao-nam",
                        new BigDecimal("450000"),
                        "Áo khoác gió 2 lớp cản gió chống nước nhẹ, tiện lợi cho hoạt động ngoài trời.",
                        true,
                        List.of(
                                new VariantSeed("AKN-05-M-BLK", "M", "Đen", 15, null),
                                new VariantSeed("AKN-05-L-GRN", "L", "Xanh Rêu", 20, new BigDecimal("470000")),
                                new VariantSeed("AKN-05-XL-GRY", "XL", "Xám", 10, new BigDecimal("470000"))
                        ),
                        "/uploads/products/demo-khoac-gio.jpg"
                ),
                // 7
                new ProductSeed(
                        "Áo len cổ tròn dệt kim nam",
                        "ao-len-co-tron-det-kim-nam",
                        "ao-nam",
                        new BigDecimal("320000"),
                        "Áo len sợi dệt kim mềm mịn, giữ nhiệt tốt, dễ phối đồ thu đông.",
                        true,
                        List.of(
                                new VariantSeed("ALN-06-M-BRN", "M", "Nâu", 12, null),
                                new VariantSeed("ALN-06-L-BEI", "L", "Be", 18, null)
                        ),
                        "/uploads/products/demo-ao-len.jpg"
                ),
                // 8
                new ProductSeed(
                        "Quần jean nam slimfit wash nhẹ",
                        "quan-jean-nam-slimfit-wash-nhe",
                        "quan-jean-nam",
                        new BigDecimal("420000"),
                        "Quần jean nam dáng ôm vừa vặn, wash màu tự nhiên, co giãn nhẹ thoải mái.",
                        true,
                        List.of(
                                new VariantSeed("QJN-07-29-LBL", "29", "Xanh Nhạt", 30, null),
                                new VariantSeed("QJN-07-30-DBL", "30", "Xanh Đậm", 25, null),
                                new VariantSeed("QJN-07-31-BLK", "31", "Đen", 15, new BigDecimal("450000")),
                                new VariantSeed("QJN-07-32-BLK", "32", "Đen", 10, new BigDecimal("450000"))
                        ),
                        "/uploads/products/demo-jean-slimfit.jpg"
                ),
                // 9
                new ProductSeed(
                        "Quần jean nam ống suông rách gối",
                        "quan-jean-nam-ong-suong-rach-goi",
                        "quan-jean-nam",
                        new BigDecimal("460000"),
                        "Jean ống suông retro phong cách đường phố, điểm rách nhẹ cá tính.",
                        true,
                        List.of(
                                new VariantSeed("QJN-08-30-LBL", "30", "Xanh Nhạt", 15, null),
                                new VariantSeed("QJN-08-31-LBL", "31", "Xanh Nhạt", 12, null),
                                new VariantSeed("QJN-08-32-SMK", "32", "Xám Khói", 8, new BigDecimal("480000"))
                        ),
                        "/uploads/products/demo-jean-suong.jpg"
                ),
                // 10
                new ProductSeed(
                        "Quần kaki nam ống đứng co giãn",
                        "quan-kaki-nam-ong-dung-co-gian",
                        "quan-kaki-nam",
                        new BigDecimal("360000"),
                        "Quần kaki công sở lịch sự, chất vải đứng phom không nhăn nhàu.",
                        true,
                        List.of(
                                new VariantSeed("QKN-09-M-BEI", "M", "Be", 20, null),
                                new VariantSeed("QKN-09-L-BLK", "L", "Đen", 15, null),
                                new VariantSeed("QKN-09-XL-GRN", "XL", "Xanh Rêu", 10, new BigDecimal("380000"))
                        ),
                        "/uploads/products/demo-kaki-ong-dung.jpg"
                ),
                // 11
                new ProductSeed(
                        "Quần tây nam xếp ly phong cách Hàn Quốc",
                        "quan-tay-nam-xep-ly-phong-cach-han-quoc",
                        "quan-nam",
                        new BigDecimal("390000"),
                        "Quần tây xếp ly thời thượng, cạp cao tôn dáng, chất vải rũ sang trọng.",
                        true,
                        List.of(
                                new VariantSeed("QTN-10-M-BLK", "M", "Đen", 22, null),
                                new VariantSeed("QTN-10-L-GRY", "L", "Ghi Xám", 18, null),
                                new VariantSeed("QTN-10-XL-LBR", "XL", "Nâu Nhạt", 14, new BigDecimal("410000"))
                        ),
                        "/uploads/products/demo-quan-tay.jpg"
                ),
                // 12
                new ProductSeed(
                        "Quần short kaki túi hộp nam",
                        "quan-short-kaki-tui-hop-nam",
                        "quan-nam",
                        new BigDecimal("220000"),
                        "Quần short kaki trẻ trung năng động, thiết kế túi hộp tiện lợi cho mùa hè.",
                        true,
                        List.of(
                                new VariantSeed("QSN-11-M-BEI", "M", "Be", 30, null),
                                new VariantSeed("QSN-11-L-BLK", "L", "Đen", 25, null),
                                new VariantSeed("QSN-11-XL-GRN", "XL", "Rêu", 20, null)
                        ),
                        "/uploads/products/demo-short-kaki.jpg"
                ),
                // 13
                new ProductSeed(
                        "Đầm hoa nhí cổ V thắt eo nữ",
                        "dam-hoa-nhi-co-v-that-eo-nu",
                        "dam-va-vay",
                        new BigDecimal("420000"),
                        "Đầm voan hoa nhí dịu dàng, cổ chữ V nữ tính, dây thắt eo tạo eo thon gọn.",
                        true,
                        List.of(
                                new VariantSeed("DHN-12-S-YEL", "S", "Hoa Vàng", 15, null),
                                new VariantSeed("DHN-12-M-YEL", "M", "Hoa Vàng", 20, null),
                                new VariantSeed("DHN-12-L-BLU", "L", "Hoa Xanh", 12, new BigDecimal("450000"))
                        ),
                        "/uploads/products/demo-dam-hoa.jpg"
                ),
                // 14
                new ProductSeed(
                        "Đầm xòe dự tiệc dáng midi thanh lịch",
                        "dam-xoe-du-tiec-dang-midi-thanh-lich",
                        "dam-va-vay",
                        new BigDecimal("580000"),
                        "Đầm dạ hội dáng midi xòe nhẹ, chất gấm dệt kim cao cấp tôn vẻ quý phái.",
                        true,
                        List.of(
                                new VariantSeed("DXD-13-S-WHT", "S", "Trắng", 10, null),
                                new VariantSeed("DXD-13-M-RED", "M", "Đỏ Mận", 15, new BigDecimal("620000")),
                                new VariantSeed("DXD-13-L-BLK", "L", "Đen", 8, new BigDecimal("620000"))
                        ),
                        "/uploads/products/demo-dam-xoe.jpg"
                ),
                // 15
                new ProductSeed(
                        "Chân váy chữ A xếp ly ngắn nữ",
                        "chan-vay-chu-a-xep-ly-ngan-nu",
                        "quan-nu",
                        new BigDecimal("240000"),
                        "Chân váy tennis chữ A có lót quần trong an toàn, phong cách nữ sinh năng động.",
                        true,
                        List.of(
                                new VariantSeed("CVN-14-S-BLK", "S", "Đen", 25, null),
                                new VariantSeed("CVN-14-M-GRY", "M", "Xám", 20, null),
                                new VariantSeed("CVN-14-L-WHT", "L", "Trắng", 15, new BigDecimal("260000"))
                        ),
                        "/uploads/products/demo-chan-vay.jpg"
                ),
                // 16
                new ProductSeed(
                        "Áo sơ mi nữ công sở lụa satin",
                        "ao-so-mi-nu-cong-so-lua-satin",
                        "ao-so-mi-nu",
                        new BigDecimal("320000"),
                        "Áo sơ mi satin bóng nhẹ sang chảnh, thiết kế chuẩn phom công sở.",
                        true,
                        List.of(
                                new VariantSeed("SMU-15-S-CRM", "S", "Trắng Kem", 20, null),
                                new VariantSeed("SMU-15-M-PNK", "M", "Hồng Pastel", 15, null),
                                new VariantSeed("SMU-15-L-MNT", "L", "Xanh Mint", 12, new BigDecimal("340000"))
                        ),
                        "/uploads/products/demo-somi-satin.jpg"
                ),
                // 17
                new ProductSeed(
                        "Áo kiểu nữ trễ vai bo chun duyên dáng",
                        "ao-kieu-nu-tre-vai-bo-chun-duyen-dang",
                        "ao-kieu-nu",
                        new BigDecimal("260000"),
                        "Áo kiểu trễ vai quyến rũ, chất tơ mềm mại, dễ phối cùng chân váy hay quần jean.",
                        true,
                        List.of(
                                new VariantSeed("AKU-16-FS-WHT", "Freesize", "Trắng", 30, null),
                                new VariantSeed("AKU-16-FS-BLK", "Freesize", "Đen", 25, null)
                        ),
                        "/uploads/products/demo-ao-tre-vai.jpg"
                ),
                // 18
                new ProductSeed(
                        "Áo cardigan nữ len dệt kim vintage",
                        "ao-cardigan-nu-len-det-kim-vintage",
                        "ao-nu",
                        new BigDecimal("350000"),
                        "Cardigan len cúc gỗ phong cách vintage cổ điển, giữ ấm nhẹ nhàng ngày se lạnh.",
                        true,
                        List.of(
                                new VariantSeed("ACN-17-S-BEI", "S", "Be", 18, null),
                                new VariantSeed("ACN-17-M-BRN", "M", "Nâu", 22, null),
                                new VariantSeed("ACN-17-L-BLU", "L", "Xanh Dương", 10, new BigDecimal("370000"))
                        ),
                        "/uploads/products/demo-cardigan.jpg"
                ),
                // 19
                new ProductSeed(
                        "Áo hoodie nỉ bông form rộng unisex",
                        "ao-hoodie-ni-bong-form-rong-unisex",
                        "ao-nam",
                        new BigDecimal("390000"),
                        "Hoodie mũ rộng trùm đầu, chất nỉ bông dày dặn, kiểu dáng oversize cá tính.",
                        true,
                        List.of(
                                new VariantSeed("HDN-18-M-GRY", "M", "Xám Tiêu", 20, null),
                                new VariantSeed("HDN-18-L-BLK", "L", "Đen", 30, null),
                                new VariantSeed("HDN-18-XL-GRN", "XL", "Xanh Rêu", 15, new BigDecimal("420000"))
                        ),
                        "/uploads/products/demo-hoodie.jpg"
                ),
                // 20
                new ProductSeed(
                        "Nón lưỡi trai thêu chữ cá tính",
                        "non-luoi-trai-theu-chu-ca-tinh",
                        "non-mu",
                        new BigDecimal("120000"),
                        "Nón kết nam nữ chất liệu kaki cotton dày dặn, khóa gài kim loại điều chỉnh độ rộng.",
                        true,
                        List.of(
                                new VariantSeed("NLT-19-FS-BLK", "Freesize", "Đen", 50, null),
                                new VariantSeed("NLT-19-FS-WHT", "Freesize", "Trắng", 40, null),
                                new VariantSeed("NLT-19-FS-BEI", "Freesize", "Be", 30, null)
                        ),
                        "/uploads/products/demo-non-luoi-trai.jpg"
                ),
                // 21
                new ProductSeed(
                        "Nón bucket vành tròn vải canvas",
                        "non-bucket-vanh-tron-vai-canvas",
                        "non-mu",
                        new BigDecimal("140000"),
                        "Mũ tai bèo vành cụp che nắng tốt, phong cách dạo phố sành điệu.",
                        true,
                        List.of(
                                new VariantSeed("NBK-20-FS-BLK", "Freesize", "Đen", 35, null),
                                new VariantSeed("NBK-20-FS-YEL", "Freesize", "Vàng Bơ", 20, null)
                        ),
                        "/uploads/products/demo-non-bucket.jpg"
                ),
                // 22
                new ProductSeed(
                        "Thắt lưng da bò nam khóa tự động cao cấp",
                        "that-lung-da-bo-nam-khoa-tu-dong-cao-cap",
                        "phu-kien",
                        new BigDecimal("280000"),
                        "Dây nịt nam da bò nguyên tấm, mặt khóa hợp kim chống gỉ mạ bóng sang trọng.",
                        true,
                        List.of(
                                new VariantSeed("TLD-21-115-BLK", "115cm", "Đen", 40, null),
                                new VariantSeed("TLD-21-120-DBR", "120cm", "Nâu Đậm", 30, null)
                        ),
                        "/uploads/products/demo-that-lung.jpg"
                ),
                // 23: INACTIVE PRODUCT #1 (isActive = false)
                new ProductSeed(
                        "Áo khoác dạ nữ dáng dài mẫu cũ",
                        "ao-khoac-da-nu-dang-dai-mau-cu",
                        "ao-nu",
                        new BigDecimal("850000"),
                        "Sản phẩm mùa cũ đã ngừng kinh doanh trên toàn hệ thống.",
                        false,
                        List.of(
                                new VariantSeed("AKD-23-S-BRN", "S", "Nâu Tây", 5, null),
                                new VariantSeed("AKD-23-M-BLK", "M", "Đen", 3, null)
                        ),
                        "/uploads/products/demo-khoac-da-inactive.jpg"
                )
        );

        for (ProductSeed ps : sampleProducts) {
            if (productRepository.existsBySlug(ps.slug())) {
                log.debug("Dev seeder: product '{}' already exists, skipping.", ps.slug());
                continue;
            }

            Category category = categoryRepository.findBySlug(ps.categorySlug())
                    .orElseGet(() -> categoryRepository.findAll().stream().findFirst().orElse(null));

            if (category == null) {
                log.warn("Dev seeder: category '{}' not found, skipping product '{}'.", ps.categorySlug(), ps.name());
                continue;
            }

            Product product = Product.builder()
                    .name(ps.name())
                    .slug(ps.slug())
                    .category(category)
                    .basePrice(ps.basePrice())
                    .description(ps.description())
                    .isActive(ps.isActive())
                    .dataSource(DataSourceType.WEBSITE)
                    .build();

            Product savedProduct = productRepository.save(product);

            // Seed variants
            for (VariantSeed vs : ps.variants()) {
                if (!productVariantRepository.existsBySku(vs.sku())) {
                    ProductVariant variant = ProductVariant.builder()
                            .product(savedProduct)
                            .sku(vs.sku())
                            .size(vs.size())
                            .color(vs.color())
                            .stock(vs.stock())
                            .price(vs.price())
                            .build();
                    productVariantRepository.save(variant);
                }
            }

            // Seed primary image
            ProductImage primaryImage = ProductImage.builder()
                    .product(savedProduct)
                    .url(ps.imageUrl())
                    .alt(ps.name())
                    .isPrimary(true)
                    .sortOrder(0)
                    .build();
            productImageRepository.save(primaryImage);

            log.info("Dev seeder: created product '{}' with {} variants.", ps.name(), ps.variants().size());
        }
    }

    private void seedSampleVouchers() {
        if (voucherRepository == null) {
            return;
        }

        List<Voucher> sampleVouchers = List.of(
                Voucher.builder()
                        .code("WELCOME10")
                        .discountType(DiscountType.PERCENT)
                        .discountValue(BigDecimal.valueOf(10))
                        .minOrder(BigDecimal.valueOf(200000))
                        .maxDiscount(BigDecimal.valueOf(50000))
                        .usageLimit(1000)
                        .perUserLimit(1)
                        .startAt(LocalDateTime.now().minusDays(1))
                        .endAt(LocalDateTime.now().plusYears(1))
                        .isActive(true)
                        .build(),
                Voucher.builder()
                        .code("ATELIER50")
                        .discountType(DiscountType.AMOUNT)
                        .discountValue(BigDecimal.valueOf(50000))
                        .minOrder(BigDecimal.valueOf(300000))
                        .maxDiscount(null)
                        .usageLimit(1000)
                        .perUserLimit(1)
                        .startAt(LocalDateTime.now().minusDays(1))
                        .endAt(LocalDateTime.now().plusYears(1))
                        .isActive(true)
                        .build()
        );

        for (Voucher v : sampleVouchers) {
            if (!voucherRepository.existsByCode(v.getCode())) {
                voucherRepository.save(v);
                log.info("Dev seeder: created sample voucher '{}'.", v.getCode());
            }
        }
    }
}
