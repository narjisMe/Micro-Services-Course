package com.ipl.product.messaging.events;

import java.math.BigDecimal;
import java.util.List;

public record StockReservedEvent(
        Long orderId,
        List<Item> items
) {
    public record Item(
            Long productId,
            String productName,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
