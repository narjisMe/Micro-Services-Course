package com.ipl.order.service;

import com.ipl.order.api.dto.OrderDtos;
import com.ipl.order.api.dto.OrderView;
import com.ipl.order.domain.ClientOrder;
import com.ipl.order.external.ClientClient;
import com.ipl.order.messaging.RabbitConfig;
import com.ipl.order.messaging.events.OrderCreatedEvent;
import com.ipl.order.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository repo;
    private final ClientClient clientClient;
    private final RabbitTemplate rabbitTemplate;

    public OrderService(
            OrderRepository repo,
            ClientClient clientClient,
            RabbitTemplate rabbitTemplate
    ) {
        this.repo = repo;
        this.clientClient = clientClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    public OrderView get(Long id) {
        ClientOrder order = repo.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Order " + id + " not found"
                        ));

        return toView(order);
    }

    public List<OrderView> all() {
        return repo.findAll().stream()
                .map(this::toView)
                .toList();
    }

    public List<OrderView> byClient(Long clientId) {
        return repo.findByClientId(clientId).stream()
                .map(this::toView)
                .toList();
    }

    public OrderView create(OrderDtos.Create req) {
        ClientClient.ClientDto client =
                clientClient.getClient(req.clientId);

        List<OrderCreatedEvent.Item> items = req.items.stream()
                .map(item -> new OrderCreatedEvent.Item(
                        item.productId,
                        item.quantity
                ))
                .toList();

        ClientOrder saved = repo.save(new ClientOrder(client.id));

        OrderCreatedEvent event =
                new OrderCreatedEvent(saved.getId(), items);

        rabbitTemplate.convertAndSend(
                RabbitConfig.ORDERS_EXCHANGE,
                RabbitConfig.ORDER_CREATED_KEY,
                event
        );

        return toView(saved);
    }

    private OrderView toView(ClientOrder order) {
        List<OrderView.Line> lines = order.getItems().stream()
                .map(item -> new OrderView.Line(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineTotal()
                ))
                .toList();

        OrderView view = new OrderView(
                order.getId(),
                order.getClientId(),
                order.getCreatedAt(),
                order.getTotal(),
                lines
        );

        view.status = order.getStatus();
        return view;
    }
}
