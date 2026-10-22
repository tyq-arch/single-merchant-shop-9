package com.shop.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 系统接口：服务信息与文档入口。 */
@RestController
public class SystemController {

    @GetMapping("/")
    public Map<String, Object> root() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "single-merchant-shop backend");
        result.put("version", "1.0.0");
        result.put("docs", "/docs");
        result.put("redoc", "/redoc");
        return result;
    }

    /** ReDoc 入口统一跳转到 Swagger UI。 */
    @GetMapping("/redoc")
    public ResponseEntity<Void> redoc() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/docs")
                .build();
    }
}
