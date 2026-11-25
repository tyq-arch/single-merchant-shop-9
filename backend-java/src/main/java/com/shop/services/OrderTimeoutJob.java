package com.shop.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 待支付订单超时清理（FR-043）：每 5 分钟扫描一次，超过 24 小时未支付的订单
 * 自动取消并回补占用库存。
 */
@Component
public class OrderTimeoutJob {

    private final OrderService orders;

    public OrderTimeoutJob(OrderService orders) {
        this.orders = orders;
    }

    @Scheduled(initialDelay = 30_000L, fixedDelay = 300_000L)
    public void sweep() {
        orders.expirePendingOrders();
    }
}
