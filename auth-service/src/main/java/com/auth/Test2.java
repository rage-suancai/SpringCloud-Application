package com.auth;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

public class Test2 {

    public static void main(String[] args) {

        //test1();
        test2();

    }

    static void test1() {

        try (JedisSentinelPool pool = new JedisSentinelPool("yxsnb",
                new HashSet<>(Arrays.asList("192.168.51.55:26379")))) {

            Jedis jedis1 = pool.getResource();
            jedis1.set("test", "114514");

            Jedis jedis2 = pool.getResource();
            System.out.println(jedis2.get("test"));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    static void test2() {

        String uuid = UUID.randomUUID().toString();
        System.out.println(uuid);

    }

}
