package com.shop.backend.settings;

import com.shop.backend.security.CurrentUser;
import com.shop.backend.security.UserPrincipal;
import com.shop.backend.settings.dto.AdminAccountResponse;
import com.shop.backend.settings.dto.ChangePasswordRequest;
import com.shop.backend.settings.dto.LogoUploadResponse;
import com.shop.backend.settings.dto.StoreSettingsRequest;
import com.shop.backend.settings.dto.StoreSettingsResponse;
import com.shop.backend.settings.dto.UpdateAdminAccountRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@Tag(name = "Admin Settings", description = "API quản lý cài đặt hệ thống và tài khoản quản trị")
public class SettingsAdminController {

    private final SettingsAdminService settingsAdminService;

    @Operation(summary = "Lấy cấu hình cửa hàng và thông số vận hành")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy cấu hình thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền quản trị")
    })
    @GetMapping("/store")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StoreSettingsResponse> getStoreSettings() {
        return ResponseEntity.ok(settingsAdminService.getStoreSettings());
    }

    @Operation(summary = "Cập nhật cấu hình cửa hàng và thông số vận hành")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu cấu hình không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền quản trị")
    })
    @PutMapping("/store")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StoreSettingsResponse> updateStoreSettings(
            @Valid @RequestBody StoreSettingsRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        String updatedBy = (currentUser != null && currentUser.getEmail() != null)
                ? currentUser.getEmail()
                : (currentUser != null ? currentUser.getUsername() : "admin");
        return ResponseEntity.ok(settingsAdminService.updateStoreSettings(request, updatedBy));
    }

    @Operation(summary = "Lấy thông tin tài khoản admin đang đăng nhập")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy thông tin tài khoản thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy tài khoản")
    })
    @GetMapping("/account")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<AdminAccountResponse> getAdminAccount(@CurrentUser UserPrincipal currentUser) {
        return ResponseEntity.ok(settingsAdminService.getAdminAccount(currentUser.getId()));
    }

    @Operation(summary = "Cập nhật thông tin cá nhân tài khoản admin đang đăng nhập")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thông tin tài khoản thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền"),
            @ApiResponse(responseCode = "409", description = "Số điện thoại đã được sử dụng")
    })
    @PutMapping("/account")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<AdminAccountResponse> updateAdminAccount(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody UpdateAdminAccountRequest request
    ) {
        return ResponseEntity.ok(settingsAdminService.updateAdminAccount(currentUser.getId(), request));
    }

    @Operation(summary = "Đổi mật khẩu tài khoản admin đang đăng nhập")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đổi mật khẩu thành công"),
            @ApiResponse(responseCode = "400", description = "Mật khẩu xác nhận không khớp hoặc trùng mật khẩu cũ"),
            @ApiResponse(responseCode = "401", description = "Mật khẩu hiện tại không chính xác")
    })
    @PutMapping("/account/password")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<Map<String, String>> changePassword(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        settingsAdminService.changePassword(currentUser.getId(), request);
        return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công"));
    }

    @Operation(summary = "Tải ảnh logo cửa hàng lên hệ thống")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tải ảnh logo thành công"),
            @ApiResponse(responseCode = "400", description = "File ảnh không hợp lệ hoặc vượt quá kích thước cho phép"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền quản trị")
    })
    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LogoUploadResponse> uploadLogo(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(settingsAdminService.uploadLogo(file));
    }
}
