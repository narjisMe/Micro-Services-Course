package com.ipl.product.messaging;

import com.ipl.product.messaging.events.OrderCreatedEvent;
import com.ipl.product.service.ProductService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedListener {
    private final ProductService productService;
    private final RabbitTemplate rabbitTemplate;

    public OrderCreatedListener(ProductService productService, RabbitTemplate rabbitTemplate) {
        this.productService = productService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitConfig.ORDER_CREATED_QUEUE)
    public void onOrderCreated(OrderCreatedEvent event) {
        System.out.println("Commande recue : " + event);
        ProductService.ReservationResult result = productService.processOrder(event);
        rabbitTemplate.convertAndSend(RabbitConfig.PRODUCTS_EXCHANGE,
                result.routingKey(), result.event());
        System.out.println("Resultat publie pour la commande " + event.orderId()
                + " : " + result.routingKey());
    }
}
