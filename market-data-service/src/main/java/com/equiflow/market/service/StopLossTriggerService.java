package com.equiflow.market.service;

import com.equiflow.market.client.OrderServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class StopLossTriggerService {

    private final OrderServiceClient orderServiceClient;

    public void evaluateTriggers(String ticker, BigDecimal currentPrice) {
        try {
            orderServiceClient.evaluateStopLoss(ticker, currentPrice);
        } catch (Exception e) {
            log.warn("Failed to evaluate stop-loss triggers for {} at {}: {}", ticker, currentPrice, e.getMessage());
        }
    }
}
