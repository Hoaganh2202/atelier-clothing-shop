package com.shop.backend.settings;

import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ConflictException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.settings.dto.AdminAccountResponse;
import com.shop.backend.settings.dto.ChangePasswordRequest;
import com.shop.backend.settings.dto.LogoUploadResponse;
import com.shop.backend.settings.dto.StoreSettingsRequest;
import com.shop.backend.settings.dto.StoreSettingsResponse;
import com.shop.backend.settings.dto.UpdateAdminAccountRequest;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsAdminServiceImpl implements SettingsAdminService {

    private static final Long SETTINGS_ID = 1L;
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2 MB

    private final StoreSettingsRepository storeSettingsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.upload.store-dir:uploads/store}")
    private String uploadDirProperty;

    private Path resolvedUploadPath;

    @PostConstruct
    public void initUploadDirectory() {
        this.resolvedUploadPath = Paths.get(uploadDirProperty).toAbsolutePath().normalize();
        try {
            if (!Files.exists(this.resolvedUploadPath)) {
                Files.createDirectories(this.resolvedUploadPath);
            }
        } catch (IOException e) {
            log.error("Failed to initialize upload directory: {}", this.resolvedUploadPath, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StoreSettingsResponse getStoreSettings() {
        StoreSettings settings = storeSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(this::createDefaultSettings);
        return toStoreSettingsResponse(settings);
    }

    @Override
    @Transactional
    public StoreSettingsResponse updateStoreSettings(StoreSettingsRequest request, String updatedBy) {
        StoreSettings settings = storeSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(this::createDefaultSettings);

        settings.setStoreName(request.getStoreName().trim());
        settings.setLegalName(StringUtils.hasText(request.getLegalName()) ? request.getLegalName().trim() : null);
        settings.setTaxCode(StringUtils.hasText(request.getTaxCode()) ? request.getTaxCode().trim() : null);
        settings.setAddress(StringUtils.hasText(request.getAddress()) ? request.getAddress().trim() : null);
        settings.setPhone(StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null);
        settings.setEmail(StringUtils.hasText(request.getEmail()) ? request.getEmail().trim().toLowerCase() : null);
        settings.setLogoUrl(StringUtils.hasText(request.getLogoUrl()) ? request.getLogoUrl().trim() : null);
        settings.setLowStockThreshold(request.getLowStockThreshold());
        settings.setDefaultShippingFee(BigDecimal.valueOf(request.getDefaultShippingFee()));
        settings.setFreeShippingThreshold(BigDecimal.valueOf(request.getFreeShippingThreshold()));
        settings.setUpdatedBy(updatedBy);

        StoreSettings saved = storeSettingsRepository.save(settings);
        return toStoreSettingsResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAccountResponse getAdminAccount(Long adminId) {
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản quản trị với id: " + adminId));
        return toAdminAccountResponse(user);
    }

    @Override
    @Transactional
    public AdminAccountResponse updateAdminAccount(Long adminId, UpdateAdminAccountRequest request) {
        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản quản trị với id: " + adminId));

        String newPhone = StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null;
        if (newPhone != null && !newPhone.equals(user.getPhone()) && userRepository.existsByPhone(newPhone)) {
            throw new ConflictException("Số điện thoại đã được sử dụng");
        }

        user.setName(request.getName().trim());
        user.setPhone(newPhone);

        User saved = userRepository.save(user);
        return toAdminAccountResponse(saved);
    }

    @Override
    @Transactional
    public void changePassword(Long adminId, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Xác nhận mật khẩu mới không khớp");
        }

        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BadRequestException("Mật khẩu mới không được trùng với mật khẩu hiện tại");
        }

        User user = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản quản trị với id: " + adminId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Mật khẩu hiện tại không chính xác");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    public LogoUploadResponse uploadLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File ảnh logo không được để trống");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("Dung lượng ảnh logo không được vượt quá 2MB");
        }

        String extension = detectImageExtension(file);
        try {
            if (!Files.exists(this.resolvedUploadPath)) {
                Files.createDirectories(this.resolvedUploadPath);
            }

            String storedFileName = "logo_" + UUID.randomUUID() + extension;
            Path destination = this.resolvedUploadPath.resolve(storedFileName).normalize();
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            return LogoUploadResponse.builder()
                    .logoUrl("/uploads/store/" + storedFileName)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Không thể lưu file ảnh vào thư mục máy chủ", e);
        }
    }

    private String detectImageExtension(MultipartFile file) {
        try {
            byte[] header = new byte[12];
            try (InputStream is = file.getInputStream()) {
                int bytesRead = is.read(header);
                if (bytesRead < 12) {
                    throw new BadRequestException("File ảnh không hợp lệ hoặc kích thước quá nhỏ");
                }
            }

            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
                return ".jpg";
            }
            if ((header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50 && (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47
                    && (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A && (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A) {
                return ".png";
            }
            if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
                return ".gif";
            }
            if ((header[0] & 0xFF) == 'R' && (header[1] & 0xFF) == 'I' && (header[2] & 0xFF) == 'F' && (header[3] & 0xFF) == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return ".webp";
            }

            throw new BadRequestException("Chỉ chấp nhận các định dạng ảnh hợp lệ: JPG, PNG, WEBP, GIF");
        } catch (IOException e) {
            throw new BadRequestException("Không thể đọc nội dung file ảnh");
        }
    }

    private StoreSettings createDefaultSettings() {
        StoreSettings defaults = StoreSettings.builder()
                .id(SETTINGS_ID)
                .storeName("Closet Shop")
                .legalName("Công ty TNHH Thời trang Closet")
                .taxCode("0101234567")
                .address("123 Đường Cầu Giấy, Phường Quan Hoa, Quận Cầu Giấy, Hà Nội")
                .phone("0987654321")
                .email("contact@closetshop.com")
                .logoUrl("/uploads/products/logo.png")
                .lowStockThreshold(10)
                .defaultShippingFee(BigDecimal.valueOf(30000))
                .freeShippingThreshold(BigDecimal.valueOf(500000))
                .updatedAt(LocalDateTime.now())
                .updatedBy("system")
                .build();
        return storeSettingsRepository.save(defaults);
    }

    private StoreSettingsResponse toStoreSettingsResponse(StoreSettings settings) {
        return StoreSettingsResponse.builder()
                .storeName(settings.getStoreName())
                .legalName(settings.getLegalName())
                .taxCode(settings.getTaxCode())
                .address(settings.getAddress())
                .phone(settings.getPhone())
                .email(settings.getEmail())
                .logoUrl(settings.getLogoUrl())
                .lowStockThreshold(settings.getLowStockThreshold())
                .defaultShippingFee(settings.getDefaultShippingFee() != null ? settings.getDefaultShippingFee().longValue() : 30000L)
                .freeShippingThreshold(settings.getFreeShippingThreshold() != null ? settings.getFreeShippingThreshold().longValue() : 500000L)
                .updatedAt(settings.getUpdatedAt())
                .updatedBy(settings.getUpdatedBy())
                .build();
    }

    private AdminAccountResponse toAdminAccountResponse(User user) {
        return AdminAccountResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole() != null ? user.getRole().getName() : "admin")
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
