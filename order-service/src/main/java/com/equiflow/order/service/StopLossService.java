package com.equiflow.order.service;

import com.equiflow.order.kafka.OrderEventPublisher;
import com.equiflow.order.model.Order;
import com.equiflow.order.model.enums.OrderStatus;
import com.equiflow.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StopLossService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    @Transactional
    public void evaluateTriggers(String ticker, BigDecimal currentPrice) {
        List<Order> triggered = orderRepository.findTriggeredStopLossOrders(ticker, currentPrice);

        if (triggered.isEmpty()) {
            return;
        }

        log.info("Evaluating {} stop-loss order(s) for {} at price {}", triggered.size(), ticker, currentPrice);

        for (Order order : triggered) {
            order.setStatus(OrderStatus.TRIGGERED);
            orderRepository.save(order);
            eventPublisher.publishStopLossTriggered(order);
            log.info("Stop-loss triggered for order {} — triggerPrice={} currentPrice={}",
                    order.getId(), order.getTriggerPrice(), currentPrice);
        }
    }
}
