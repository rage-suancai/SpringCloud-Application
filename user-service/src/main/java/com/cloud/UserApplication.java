package com.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;

// @EnableOAuth2Sso
@EnableResourceServer
@SpringBootApplication
public class UserApplication {

    public static void main(String[] args) {

        SpringApplication.run(UserApplication.class, args);

    }

}
