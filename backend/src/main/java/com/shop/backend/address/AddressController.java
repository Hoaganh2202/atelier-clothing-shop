package com.shop.backend.address;

import com.shop.backend.address.dto.AddressRequest;
import com.shop.backend.address.dto.AddressResponse;
import com.shop.backend.common.dto.ErrorResponse;
import com.shop.backend.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
@Tag(name = "Customer Address Book", description = "Quản lý sổ địa chỉ giao hàng của người dùng đang đăng nhập")
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    @Operation(
            summary = "Lấy danh sách địa chỉ giao hàng của người dùng đang đăng nhập",
            description = "Sắp xếp địa chỉ mặc định lên đầu, sau đó theo địa chỉ mới nhất (id giảm dần)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách địa chỉ thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<AddressResponse>> getMyAddresses(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(addressService.getMyAddresses(principal.getId()));
    }

    @PostMapping
    @Operation(
            summary = "Thêm địa chỉ giao hàng mới",
            description = "Địa chỉ đầu tiên tự động được đặt làm mặc định. Mỗi tài khoản tối đa 10 địa chỉ."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo địa chỉ thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc vượt quá giới hạn 10 địa chỉ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AddressResponse> createAddress(
            @Valid @RequestBody AddressRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        AddressResponse created = addressService.createAddress(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Cập nhật địa chỉ giao hàng theo ID",
            description = "Chỉ cho phép sửa địa chỉ thuộc về người dùng đang đăng nhập. Nếu địa chỉ không tồn tại hoặc của người khác sẽ trả về 404."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật địa chỉ thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy địa chỉ giao hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AddressResponse> updateAddress(
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(addressService.updateAddress(principal.getId(), id, request));
    }

    @PatchMapping("/{id}/default")
    @Operation(
            summary = "Đặt một địa chỉ làm mặc định",
            description = "Đảm bảo mỗi tài khoản chỉ có duy nhất một địa chỉ mặc định tại mọi thời điểm."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đặt địa chỉ mặc định thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy địa chỉ giao hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AddressResponse> setDefaultAddress(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(addressService.setDefaultAddress(principal.getId(), id));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Xóa địa chỉ giao hàng theo ID",
            description = "Nếu xóa địa chỉ mặc định và người dùng còn địa chỉ khác, địa chỉ còn lại mới nhất sẽ tự động trở thành mặc định."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xóa địa chỉ thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy địa chỉ giao hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteAddress(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        addressService.deleteAddress(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
