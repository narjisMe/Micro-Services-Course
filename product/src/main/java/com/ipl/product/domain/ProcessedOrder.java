package com.ipl.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

@Entity
public class ProcessedOrder {
    @Id
    private Long orderId;
    @Column(nullable = false)
    private String routingKey;
    @Lob
    @Column(nullable = false)
    private String resultJson;

    protected ProcessedOrder() {}

    public ProcessedOrder(Long orderId, String routingKey, String resultJson) {
        this.orderId = orderId;
        this.routingKey = routingKey;
        this.resultJson = resultJson;
    }

    public String getRoutingKey() { return routingKey; }
    public String getResultJson() { return resultJson; }
}
