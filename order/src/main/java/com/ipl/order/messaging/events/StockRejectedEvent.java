package com.ipl.order.messaging.events;

public record StockRejectedEvent(
        Long orderId,
        String reason
) {}


