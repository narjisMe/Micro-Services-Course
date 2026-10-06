package com.ipl.product.messaging.events;

public record StockRejectedEvent(
        Long orderId,
        String reason
) {}
