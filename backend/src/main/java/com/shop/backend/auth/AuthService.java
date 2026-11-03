package com.shop.backend.auth;

import com.shop.backend.auth.dto.AuthResponse;
import com.shop.backend.auth.dto.ChangePasswordRequest;
import com.shop.backend.auth.dto.LoginRequest;
import com.shop.backend.auth.dto.RegisterRequest;
import com.shop.backend.auth.dto.UpdateProfileRequest;
import com.shop.backend.auth.dto.UserSummaryResponse;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ConflictException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.security.JwtService;
import com.shop.backend.security.UserPrincipal;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final int MAX_PASSWORD_BYTES = 72;
    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_PASSWORD_LENGTH = 72;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        boolean hasEmail = StringUtils.hasText(request.email());
        boolean hasPhone = StringUtils.hasText(request.phone());

        if (!hasEmail && !hasPhone) {
            throw new BadRequestException("Phải cung cấp ít nhất email hoặc số điện thoại");
        }

        validatePasswordPolicy(request.password(), "Mật khẩu");

        String normalizedEmail = hasEmail ? request.email().trim().toLowerCase() : null;
        String normalizedPhone = hasPhone ? request.phone().trim() : null;

        if (normalizedEmail != null && userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email đã được sử dụng");
        }

        if (normalizedPhone != null && userRepository.existsByPhone(normalizedPhone)) {
            throw new ConflictException("Số điện thoại đã được sử dụng");
        }

        Role customerRole = roleRepository.findByName("customer")
                .orElseThrow(() -> new IllegalStateException("Vai trò 'customer' chưa được khởi tạo"));

        User user = User.builder()
                .name(request.name().trim())
                .email(normalizedEmail)
                .phone(normalizedPhone)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(customerRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build();

        User savedUser = userRepository.save(user);
        String token = jwtService.generateToken(savedUser);

        return new AuthResponse(token, toUserSummary(savedUser));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = request.email().trim();

        Optional<User> userOptional = userRepository.findByEmail(identifier.toLowerCase());
        if (userOptional.isEmpty()) {
            userOptional = userRepository.findByPhone(identifier);
        }

        User user = userOptional.orElseThrow(() ->
                new BadCredentialsException("Email hoặc mật khẩu không chính xác"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Email hoặc mật khẩu không chính xác");
        }

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new LockedException("Tài khoản của bạn đã bị khóa");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, toUserSummary(user));
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse getMe(UserPrincipal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Chưa xác thực danh tính");
        }

        User user = userRepository.findWithRoleById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new LockedException("Tài khoản của bạn đã bị khóa");
        }

        return toUserSummary(user);
    }

    @Transactional
    public UserSummaryResponse updateProfile(UserPrincipal principal, UpdateProfileRequest request) {
        if (principal == null) {
            throw new BadCredentialsException("Chưa xác thực danh tính");
        }

        User user = userRepository.findWithRoleById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new LockedException("Tài khoản của bạn đã bị khóa");
        }

        String trimmedName = request.name() != null ? request.name().trim() : "";
        if (!StringUtils.hasText(trimmedName)) {
            throw new BadRequestException("Họ tên không được để trống");
        }
        if (trimmedName.length() > 150) {
            throw new BadRequestException("Họ tên không được vượt quá 150 ký tự");
        }

        String normalizedPhone = StringUtils.hasText(request.phone()) ? request.phone().trim() : null;
        if (normalizedPhone != null) {
            if (!normalizedPhone.matches("^0[0-9]{9}$")) {
                throw new BadRequestException("Số điện thoại không hợp lệ");
            }
            if (userRepository.existsByPhoneAndIdNot(normalizedPhone, user.getId())) {
                throw new ConflictException("Số điện thoại đã được sử dụng");
            }
        } else {
            if (!StringUtils.hasText(user.getEmail())) {
                throw new BadRequestException("Tài khoản phải có ít nhất email hoặc số điện thoại");
            }
        }

        if (request.birthDate() != null && request.birthDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Ngày sinh không được nằm trong tương lai");
        }

        user.setName(trimmedName);
        user.setPhone(normalizedPhone);
        user.setGender(request.gender());
        user.setBirthDate(request.birthDate());

        User updatedUser = userRepository.save(user);
        return toUserSummary(updatedUser);
    }

    @Transactional
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        if (principal == null) {
            throw new BadCredentialsException("Chưa xác thực danh tính");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new LockedException("Tài khoản của bạn đã bị khóa");
        }

        if (!StringUtils.hasText(request.currentPassword())) {
            throw new BadRequestException("Mật khẩu hiện tại không được để trống");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu hiện tại không chính xác");
        }

        validatePasswordPolicy(request.newPassword(), "Mật khẩu mới");

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private void validatePasswordPolicy(String password, String fieldLabel) {
        if (!StringUtils.hasText(password)) {
            throw new BadRequestException(fieldLabel + " không được để trống");
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new BadRequestException(fieldLabel + " phải từ 6 đến 72 ký tự");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BadRequestException(fieldLabel + " không được vượt quá " + MAX_PASSWORD_BYTES + " byte khi mã hóa UTF-8");
        }
    }

    private UserSummaryResponse toUserSummary(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getGender(),
                user.getBirthDate(),
                user.getRole().getName()
        );
    }
}
