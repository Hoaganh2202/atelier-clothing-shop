package com.shop.backend.settings;

import com.shop.backend.settings.dto.AdminAccountResponse;
import com.shop.backend.settings.dto.ChangePasswordRequest;
import com.shop.backend.settings.dto.LogoUploadResponse;
import com.shop.backend.settings.dto.StoreSettingsRequest;
import com.shop.backend.settings.dto.StoreSettingsResponse;
import com.shop.backend.settings.dto.UpdateAdminAccountRequest;
import org.springframework.web.multipart.MultipartFile;

public interface SettingsAdminService {

    StoreSettingsResponse getStoreSettings();

    StoreSettingsResponse updateStoreSettings(StoreSettingsRequest request, String updatedBy);

    AdminAccountResponse getAdminAccount(Long adminId);

    AdminAccountResponse updateAdminAccount(Long adminId, UpdateAdminAccountRequest request);

    void changePassword(Long adminId, ChangePasswordRequest request);

    LogoUploadResponse uploadLogo(MultipartFile file);
}
