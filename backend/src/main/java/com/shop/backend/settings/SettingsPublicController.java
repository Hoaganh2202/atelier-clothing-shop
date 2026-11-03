package com.shop.backend.settings;

import com.shop.backend.settings.dto.ShippingPolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "Store Settings Public", description = "API công khai đọc chính sách cấu hình cửa hàng dành cho khách hàng")
public class SettingsPublicController {

    private final StoreSettingsReader storeSettingsReader;

    @GetMapping("/shipping")
    @Operation(summary = "Lấy chính sách phí vận chuyển công khai")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy chính sách vận chuyển thành công")
    })
    public ResponseEntity<ShippingPolicyResponse> getShippingPolicy() {
        return ResponseEntity.ok(ShippingPolicyResponse.builder()
                .shippingFee(storeSettingsReader.getDefaultShippingFee().longValue())
                .freeShippingThreshold(storeSettingsReader.getFreeShippingThreshold().longValue())
                .build());
    }
}
