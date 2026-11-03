package com.shop.backend.address;

import com.shop.backend.address.dto.AddressRequest;
import com.shop.backend.address.dto.AddressResponse;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.user.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressService {

    public static final int MAX_ADDRESSES_PER_USER = 10;
    private static final String ADDRESS_NOT_FOUND_MESSAGE = "Không tìm thấy địa chỉ giao hàng";

    private final UserAddressRepository userAddressRepository;
    private final EntityManager entityManager;

    public List<AddressResponse> getMyAddresses(Long userId) {
        return userAddressRepository.findAllByUserIdOrdered(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public UserAddress getUserAddress(Long userId, Long addressId) {
        return userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new BadRequestException("Địa chỉ giao hàng không hợp lệ hoặc không thuộc tài khoản của bạn"));
    }

    @Transactional
    public AddressResponse createAddress(Long userId, AddressRequest request) {
        // Acquire a pessimistic write lock on the user's existing addresses to prevent concurrent
        // requests from bypassing the 10-address cap or creating multiple default addresses.
        List<UserAddress> existingAddresses = userAddressRepository.findAllByUserIdForUpdate(userId);
        if (existingAddresses.size() >= MAX_ADDRESSES_PER_USER) {
            throw new BadRequestException("Mỗi tài khoản chỉ được lưu tối đa 10 địa chỉ giao hàng");
        }

        boolean shouldBeDefault = existingAddresses.isEmpty() || Boolean.TRUE.equals(request.getIsDefault());
        User userRef = entityManager.getReference(User.class, userId);

        UserAddress newAddress = UserAddress.builder()
                .user(userRef)
                .receiverName(request.getReceiverName().trim())
                .phone(request.getPhone().trim())
                .province(request.getProvince().trim())
                .district(normalizeOptionalText(request.getDistrict()))
                .ward(normalizeOptionalText(request.getWard()))
                .address(request.getAddress().trim())
                .isDefault(shouldBeDefault)
                .build();

        UserAddress savedAddress = userAddressRepository.saveAndFlush(newAddress);

        if (shouldBeDefault && !existingAddresses.isEmpty()) {
            userAddressRepository.clearOtherDefaults(userId, savedAddress.getId());
        }

        return toResponse(savedAddress);
    }

    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request) {
        List<UserAddress> existingAddresses = userAddressRepository.findAllByUserIdForUpdate(userId);
        UserAddress target = findTargetAddressOrThrow(existingAddresses, addressId);

        target.setReceiverName(request.getReceiverName().trim());
        target.setPhone(request.getPhone().trim());
        target.setProvince(request.getProvince().trim());
        target.setDistrict(normalizeOptionalText(request.getDistrict()));
        target.setWard(normalizeOptionalText(request.getWard()));
        target.setAddress(request.getAddress().trim());

        boolean promoteToDefault = !Boolean.TRUE.equals(target.getIsDefault())
                && Boolean.TRUE.equals(request.getIsDefault());
        if (promoteToDefault) {
            target.setIsDefault(true);
        }

        UserAddress saved = userAddressRepository.saveAndFlush(target);
        if (promoteToDefault) {
            userAddressRepository.clearOtherDefaults(userId, saved.getId());
        }
        return toResponse(saved);
    }

    @Transactional
    public AddressResponse setDefaultAddress(Long userId, Long addressId) {
        List<UserAddress> existingAddresses = userAddressRepository.findAllByUserIdForUpdate(userId);
        UserAddress target = findTargetAddressOrThrow(existingAddresses, addressId);

        if (!Boolean.TRUE.equals(target.getIsDefault())) {
            target.setIsDefault(true);
            target = userAddressRepository.saveAndFlush(target);
        }
        userAddressRepository.clearOtherDefaults(userId, target.getId());

        return toResponse(target);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        List<UserAddress> existingAddresses = userAddressRepository.findAllByUserIdForUpdate(userId);
        UserAddress target = findTargetAddressOrThrow(existingAddresses, addressId);
        boolean wasDefault = Boolean.TRUE.equals(target.getIsDefault());

        userAddressRepository.delete(target);
        userAddressRepository.flush();

        if (wasDefault) {
            existingAddresses.stream()
                    .filter(addr -> !addr.getId().equals(addressId))
                    .findFirst()
                    .ifPresent(newestRemaining -> {
                        newestRemaining.setIsDefault(true);
                        userAddressRepository.saveAndFlush(newestRemaining);
                    });
        }
    }

    private UserAddress findTargetAddressOrThrow(List<UserAddress> userAddresses, Long addressId) {
        return userAddresses.stream()
                .filter(addr -> addr.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ADDRESS_NOT_FOUND_MESSAGE));
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AddressResponse toResponse(UserAddress entity) {
        return AddressResponse.builder()
                .id(entity.getId())
                .receiverName(entity.getReceiverName())
                .phone(entity.getPhone())
                .province(entity.getProvince())
                .district(entity.getDistrict())
                .ward(entity.getWard())
                .address(entity.getAddress())
                .isDefault(entity.getIsDefault())
                .build();
    }
}
