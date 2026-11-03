package com.shop.backend.review;

import com.shop.backend.review.dto.CreateReviewRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Validation hợp lệ khi rating từ 1 đến 5 và content dưới 1000 ký tự")
    void validRequest_HasNoViolations() {
        CreateReviewRequest request = new CreateReviewRequest(5, "Chất lượng tốt");
        Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Validation lỗi khi rating là null")
    void ratingNull_HasViolation() {
        CreateReviewRequest request = new CreateReviewRequest(null, "Chất lượng tốt");
        Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("Số sao đánh giá không được để trống");
    }

    @Test
    @DisplayName("Validation lỗi khi rating nhỏ hơn 1")
    void ratingLessThanOne_HasViolation() {
        CreateReviewRequest request = new CreateReviewRequest(0, "Chất lượng tốt");
        Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("Đánh giá tối thiểu 1 sao");
    }

    @Test
    @DisplayName("Validation lỗi khi rating lớn hơn 5")
    void ratingGreaterThanFive_HasViolation() {
        CreateReviewRequest request = new CreateReviewRequest(6, "Chất lượng tốt");
        Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("Đánh giá tối đa 5 sao");
    }

    @Test
    @DisplayName("Validation lỗi khi content vượt quá 1000 ký tự")
    void contentExceedsMaxLength_HasViolation() {
        String longContent = "A".repeat(1001);
        CreateReviewRequest request = new CreateReviewRequest(5, longContent);
        Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("tối đa 1000 ký tự");
    }

    @Test
    @DisplayName("Validation hợp lệ khi content null (chỉ đánh giá số sao)")
    void contentNull_IsValid() {
        CreateReviewRequest request = new CreateReviewRequest(4, null);
        Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }
}
