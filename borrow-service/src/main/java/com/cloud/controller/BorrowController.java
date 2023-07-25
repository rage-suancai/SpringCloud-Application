package com.cloud.controller;

import com.cloud.dto.UserBorrowDetail;
import com.cloud.service.BorrowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class BorrowController {

    @Resource
    private BorrowService borrowService;
    private int BorrowCount = 0;

    @GetMapping("/api/borrow1/{uid}")
    public UserBorrowDetail getUserBorrowDetailByUid1(@PathVariable("uid") Integer uid) {

        int count = BorrowCount++; System.err.println("调用了借阅服务" + count + "次");
        return borrowService.getUserBorrowDetailByUid(uid);

    }

}
