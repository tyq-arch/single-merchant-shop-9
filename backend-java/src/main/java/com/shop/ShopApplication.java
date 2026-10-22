package com.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 在线购物系统（单卖家版）后端入口。
 *
 * <p>启动：{@code mvn spring-boot:run} 或 {@code java -jar target/shop-backend-1.0.0.jar}
 * <br>接口文档：http://127.0.0.1:8000/docs
 */
@SpringBootApplication
public class ShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
