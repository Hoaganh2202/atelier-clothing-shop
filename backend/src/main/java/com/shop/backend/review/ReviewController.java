package com.shop.backend.review;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.review.dto.CreateReviewRequest;
import com.shop.backend.review.dto.ReviewEligibilityResponse;
import com.shop.backend.review.dto.ReviewResponse;
import com.shop.backend.review.dto.ReviewSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<PageResponse<ReviewResponse>> list(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        return ResponseEntity.ok(reviewService.listReviews(productId, page, size));
    }

    @GetMapping("/summary")
    public ResponseEntity<ReviewSummaryResponse> summary(@PathVariable Long productId) {
        return ResponseEntity.ok(reviewService.getReviewSummary(productId));
    }

    @GetMapping("/eligibility")
    public ResponseEntity<ReviewEligibilityResponse> eligibility(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        String email = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(reviewService.checkEligibility(email, productId));
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReviewResponse> create(
            Authentication authentication,
            @PathVariable Long productId,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        ReviewResponse response = reviewService.createReview(authentication.getName(), productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
