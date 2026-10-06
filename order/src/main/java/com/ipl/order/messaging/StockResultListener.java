package com.ipl.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipl.order.domain.ClientOrder;
import com.ipl.order.domain.OrderItem;
import com.ipl.order.domain.OrderStatus;
import com.ipl.order.messaging.events.StockReservedEvent;
import com.ipl.order.messaging.events.StockRejectedEvent;
import com.ipl.order.repository.OrderRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Component
public class StockResultListener {
    private final OrderRepository repository;
    private final ObjectMapper objectMapper;

    public StockResultListener(OrderRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.STOCK_RESULT_QUEUE)
    @Transactional
    public void onStockResult(Message message) throws IOException {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();

        if (RabbitConfig.STOCK_RESERVED_KEY.equals(routingKey)) {
            StockReservedEvent event = objectMapper.readValue(
                    message.getBody(), StockReservedEvent.class);
            ClientOrder order = findOrder(event.orderId());
            if (order.getStatus() != OrderStatus.PENDING) {
                return;
            }
            for (StockReservedEvent.Item item : event.items()) {
                order.addItem(new OrderItem(item.productId(), item.productName(),
                        item.quantity(), item.unitPrice()));
            }
            order.setStatus(OrderStatus.CONFIRMED);
            repository.save(order);
            System.out.println("Commande confirmee : " + order.getId());

        } else if (RabbitConfig.STOCK_REJECTED_KEY.equals(routingKey)) {
            StockRejectedEvent event = objectMapper.readValue(
                    message.getBody(), StockRejectedEvent.class);
            ClientOrder order = findOrder(event.orderId());
            if (order.getStatus() != OrderStatus.PENDING) {
                return;
            }
            order.setStatus(OrderStatus.REJECTED);
            repository.save(order);
            System.out.println("Commande refusee : " + order.getId()
                    + " - " + event.reason());
        } else {
            throw new IllegalArgumentException("Routing key inconnue : " + routingKey);
        }
    }

    private ClientOrder findOrder(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Commande introuvable : " + id));
    }
}
