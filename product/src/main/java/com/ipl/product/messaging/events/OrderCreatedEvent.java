package com.ipl.product.messaging.events;

import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        List<Item> items
) {
    public record Item(
            Long productId,
            int quantity
    ) {}
}
