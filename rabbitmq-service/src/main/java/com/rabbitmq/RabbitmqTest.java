package com.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class RabbitmqTest {

    private static final ConnectionFactory factory = new ConnectionFactory();

    public static void main(String[] args) throws IOException, TimeoutException {

        // Create();

        GetMessage();

    }

    static void Create() {

        factory.setHost("192.168.43.128");
        factory.setPort(5672);
        factory.setUsername("admin");
        factory.setPassword("admin");
        factory.setVirtualHost("/test");

        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {

            channel.queueDeclare("yyds", false, false, false, null);
            channel.queueBind("yyds", "amq.direct", "my-yyds");
            channel.basicPublish("amq.direct", "my-yyds", null, "Hello RabbitMQ".getBytes());

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    static void GetMessage() throws IOException, TimeoutException {

        factory.setHost("192.168.43.128");
        factory.setPort(5672);
        factory.setUsername("admin");
        factory.setPassword("admin");
        factory.setVirtualHost("/test");

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.basicConsume("yyds", false, (s, delivery) -> {
            System.out.println(new String(delivery.getBody()));
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        }, s -> {});

    }

}
