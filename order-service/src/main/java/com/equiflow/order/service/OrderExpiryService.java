package com.equiflow.order.service;

import com.equiflow.order.kafka.OrderEventPublisher;
import com.equiflow.order.model.Order;
import com.equiflow.order.model.enums.OrderStatus;
import com.equiflow.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpiryService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    /**
     * Runs at 4PM ET (21:00 UTC) Monday-Friday.
     * Cancels all OPEN limit orders at market close.
     */
    @Scheduled(cron = "0 0 21 * * MON-FRI", zone = "UTC")
    @Transactional
    public void expireOpenLimitOrders() {
        log.info("Market close: expiring all open limit orders");

        List<Order> openOrders = orderRepository.findByStatus(OrderStatus.OPEN);
        int cancelled = 0;

        for (Order order : openOrders) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setRejectionReason("Cancelled at market close - day order expired");
            orderRepository.save(order);
            eventPublisher.publishOrderCancelled(order);
            cancelled++;
        }

        List<Order> partialOrders = orderRepository.findByStatus(OrderStatus.PARTIALLY_FILLED);
        for (Order order : partialOrders) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setRejectionReason("Partially filled order cancelled at market close");
            orderRepository.save(order);
            eventPublisher.publishOrderCancelled(order);
            cancelled++;
        }

        log.info("Expired {} open orders at market close", cancelled);
    }
}
