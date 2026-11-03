package com.shop.backend.einvoice.dto;

import com.shop.backend.einvoice.model.ProofSource;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFulfillmentEventCommand {
    private Long orderId;
    private String fulfillmentCode;
    private String carrierName;
    private String trackingNumber;
    private LocalDateTime deliveredAt;
    private ProofSource proofSource;
    private String proofReference;
    private String note;
    private List<CreateFulfillmentItemCommand> items;
}
