package com.ipl.product.service;
import com.ipl.product.api.dto.ProductDtos;
import com.ipl.product.domain.Product;
import com.ipl.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

import com.ipl.product.messaging.events.OrderCreatedEvent;
import com.ipl.product.messaging.events.StockReservedEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipl.product.domain.ProcessedOrder;
import com.ipl.product.repository.ProcessedOrderRepository;
import com.ipl.product.messaging.RabbitConfig;
import com.ipl.product.messaging.events.StockRejectedEvent;

@Service
public class ProductService {
    private final ProductRepository repo;

    private final ProcessedOrderRepository processed;
    private final ObjectMapper objectMapper;

    public ProductService(ProductRepository repo, ProcessedOrderRepository processed,
                          ObjectMapper objectMapper) {
        this.repo = repo;
        this.processed = processed;
        this.objectMapper = objectMapper;
    }

    public record ReservationResult(String routingKey, Object event) {}

    @Transactional
    public ReservationResult processOrder(OrderCreatedEvent event) {
        if (event == null || event.orderId() == null || event.items() == null) {
            throw new IllegalArgumentException("INVALID_ORDER_EVENT");
        }

        ProcessedOrder previous = processed.findById(event.orderId()).orElse(null);
        if (previous != null) {
            System.out.println("Doublon ignore : " + event.orderId()
                    + " (resultat republie)");
            return restoreResult(previous);
        }

        ReservationResult result;
        try {
            result = new ReservationResult(RabbitConfig.STOCK_RESERVED_KEY,
                    reserveOrderOnce(event));
        } catch (EntityNotFoundException exception) {
            result = rejected(event.orderId(), "PRODUCT_NOT_FOUND");
        } catch (IllegalStateException exception) {
            result = rejected(event.orderId(), exception.getMessage());
        }

        try {
            processed.save(new ProcessedOrder(event.orderId(), result.routingKey(),
                    objectMapper.writeValueAsString(result.event())));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize reservation result", exception);
        }
        return result;
    }

    private ReservationResult rejected(Long id, String reason) {
        return new ReservationResult(RabbitConfig.STOCK_REJECTED_KEY,
                new StockRejectedEvent(id, reason));
    }

    private ReservationResult restoreResult(ProcessedOrder previous) {
        try {
            Object event = RabbitConfig.STOCK_RESERVED_KEY.equals(previous.getRoutingKey())
                    ? objectMapper.readValue(previous.getResultJson(), StockReservedEvent.class)
                    : objectMapper.readValue(previous.getResultJson(), StockRejectedEvent.class);
            return new ReservationResult(previous.getRoutingKey(), event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read saved reservation result", exception);
        }
    }

    public List<Product> all() {
        return repo.findAll();
    }

    public Product get(Long id){
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Product "+id+" not found"));
    }

    public Product create(ProductDtos.Create d){
        return repo.save(new Product(d.name,d.price,d.stock));
    }

    public Product update(Long id, ProductDtos.Update d){
        Product p = get(id);
        p.setName(d.name);
        p.setPrice(d.price);
        p.setStock(d.stock);
        return repo.save(p);
    }

    public void delete(Long id) {
        repo.delete(get(id));
    }

    @Transactional
    public void reserve(Long id, int qty){
        Product p = get(id);
        if(p.getStock() < qty) throw new IllegalStateException("INSUFFICIENT_STOCK");
        p.setStock(p.getStock() - qty);
    }

    private StockReservedEvent reserveOrderOnce(OrderCreatedEvent event) {
        if (event.items().isEmpty()) {
            throw new IllegalStateException("EMPTY_ORDER");
        }
        for (OrderCreatedEvent.Item item : event.items()) {
            if (item == null || item.productId() == null || item.quantity() <= 0) {
                throw new IllegalStateException("INVALID_ORDER_ITEM");
            }
        }
        Map<Long, Integer> quantities = new LinkedHashMap<>();

        for (OrderCreatedEvent.Item item : event.items()) {
            quantities.merge(
                    item.productId(),
                    item.quantity(),
                    Integer::sum
            );
        }

        Map<Long, Product> products = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = get(entry.getKey());

            if (product.getStock() < entry.getValue()) {
                throw new IllegalStateException("INSUFFICIENT_STOCK");
            }

            products.put(entry.getKey(), product);
        }

        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = products.get(entry.getKey());
            product.setStock(product.getStock() - entry.getValue());
        }

        List<StockReservedEvent.Item> reservedItems =
                quantities.entrySet().stream()
                        .map(entry -> {
                            Product product = products.get(entry.getKey());

                            return new StockReservedEvent.Item(
                                    product.getId(),
                                    product.getName(),
                                    entry.getValue(),
                                    product.getPrice()
                            );
                        })
                        .toList();

        return new StockReservedEvent(event.orderId(), reservedItems);
    }
}
