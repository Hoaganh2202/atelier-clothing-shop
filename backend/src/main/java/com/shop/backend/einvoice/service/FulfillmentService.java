package com.shop.backend.einvoice.service;

import com.shop.backend.einvoice.dto.CreateFulfillmentEventCommand;
import com.shop.backend.einvoice.model.FulfillmentEvent;
import java.util.List;

public interface FulfillmentService {
    FulfillmentEvent recordFulfillment(CreateFulfillmentEventCommand command);
    FulfillmentEvent getFulfillmentEvent(Long id);
    List<FulfillmentEvent> getFulfillmentsForOrder(Long orderId);
}
