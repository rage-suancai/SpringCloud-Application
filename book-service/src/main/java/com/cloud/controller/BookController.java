package com.cloud.controller;

import com.cloud.service.BookService;
import com.entity.Book;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class BookController {

    @Resource
    private BookService bookService;

    private int BookCount = 0;

    /*@GetMapping("/api/book/{bid}")
    public Book findBookById(@PathVariable Integer bid) {

        int count = BookCount++; System.err.println("调用图书服务" + count + "次");
        return bookService.getBookById(bid);

    }*/

    @GetMapping("/api/book/{bid}")
    public Book findBookById(@PathVariable Integer bid) {

        SecurityContext context = SecurityContextHolder.getContext();
        System.out.println(context.getAuthentication());
        return bookService.getBookById(bid);

    }

}
