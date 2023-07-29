package com.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;

@EnableFeignClients
//@EnableOAuth2Sso
@EnableResourceServer
@SpringBootApplication
public class BorrowApplication {

    public static void main(String[] args) {

        SpringApplication.run(BorrowApplication.class, args);

    }

}
