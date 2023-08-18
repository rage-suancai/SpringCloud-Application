package com.rabbitmq.controller;

import com.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class SpringCloudMqApplicationTests {

    @Resource
    private RabbitTemplate template;

    @Test
    public void publisher1() {
        template.convertAndSend("amq.direct", "my-yyds", "Hello RabbitMQ");
    }

    @Test
    public void publisher2() {

        Object res = template.convertSendAndReceive("amq.direct", "my-yyds", "Hello RabbitMQ");
        System.out.println("收到消费者响应: " + res);

    }

    @Test
    public void publisher3() {
        template.convertAndSend("amq.direct", "yyds", new User());
    }

}
