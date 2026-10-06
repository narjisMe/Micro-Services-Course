package com.ipl.product.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String ORDERS_EXCHANGE = "orders.exchange";
    public static final String ORDER_CREATED_QUEUE = "order.created.queue";
    public static final String ORDER_CREATED_KEY = "order.created";

    public static final String PRODUCTS_EXCHANGE = "products.exchange";
    public static final String STOCK_RESULT_QUEUE = "order.stock-result.queue";
    public static final String STOCK_RESERVED_KEY = "stock.reserved";
    public static final String STOCK_REJECTED_KEY = "stock.rejected";

    @Bean
    public TopicExchange ordersExchange() {
        return new TopicExchange(ORDERS_EXCHANGE);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(ORDER_CREATED_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(ORDER_CREATED_QUEUE + ".dlq")
                .build();
    }

    @Bean
    public Queue orderCreatedDlq() {
        return QueueBuilder.durable(ORDER_CREATED_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder.bind(orderCreatedQueue())
                .to(ordersExchange())
                .with(ORDER_CREATED_KEY);
    }

    @Bean
    public TopicExchange productsExchange() {
        return new TopicExchange(PRODUCTS_EXCHANGE);
    }

    @Bean
    public Queue stockResultQueue() {
        return new Queue(STOCK_RESULT_QUEUE, true);
    }

    @Bean
    public Binding stockResultBinding() {
        return BindingBuilder.bind(stockResultQueue())
                .to(productsExchange())
                .with("stock.*");
    }

    @Bean
    public MessageConverter jsonConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
