package com.cloud.service.client;

import com.entity.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient("user-service")
public interface UserClient {

    @GetMapping("/api/user/{uid}")
    User getUserById(@PathVariable("uid") int uid);

}
