package com.auth;

import java.util.Base64;

public class Test {

    public static void main(String[] args) {

        //test1();
        test2();

    }

    public static void test1() {

        String str = "你们可能不知道只用20万赢到578万元是什么概念";

        String encodeStr = Base64.getEncoder().encodeToString(str.getBytes());
        System.out.println("Base64编码后的字符串: " + encodeStr);
        System.out.println("解码后的字符串: " + new String(Base64.getDecoder().decode(encodeStr)));

    }

    public static void test2() {

        String str1 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
        String str2 = "eyJleHAiOjE2OTA2Nzc1MDIsInVzZXJfbmFtZSI6InRlc3QiLCJhdXRob3JpdGllcyI6WyJST0xFX3VzZXIiXSwianRpIjoiNWEzZDc3ZTUtZGUxNS00YmRiLThiOTYtYzRhMTI5YTc2N2QyIiwiY2xpZW50X2lkIjoid2ViIiwic2NvcGUiOlsiYm9vayIsInVzZXIiLCJib3Jyb3ciXX0";
        // String str3 = "pE70U33ecFd-tbULZ-YbdJ3AOZ8gJs0fK6mrXXqKBiM";

        System.out.println("解码后的字符串1: " + new String(Base64.getDecoder().decode(str1)));
        System.out.println("解码后的字符串2: " + new String(Base64.getDecoder().decode(str2)));
        // System.out.println("解码后的字符串3: " + new String(Base64.getDecoder().decode(str3)));

    }

}