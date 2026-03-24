package com.equiflow.market.service;

import com.equiflow.market.model.TickerPrice;
import com.equiflow.market.repository.TickerPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final TickerPriceRepository tickerPriceRepository;
    private final ScenarioEngine scenarioEngine;
    private final StopLossTriggerService stopLossTriggerService;
    private final Random random = new Random();

    public TickerPrice getPrice(String ticker) {
        return tickerPriceRepository.findByTicker(ticker.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Unknown ticker: " + ticker));
    }

    public List<TickerPrice> getAllPrices() {
        return tickerPriceRepository.findAll();
    }

    @Transactional
    public TickerPrice simulateTick(String ticker) {
        TickerPrice price = getPrice(ticker);

        // Apply small random walk (±0.5%)
        double changePct = (random.nextGaussian() * 0.005);
        BigDecimal change = price.getCurrentPrice().multiply(BigDecimal.valueOf(changePct));
        BigDecimal newPrice = price.getCurrentPrice().add(change)
                .max(BigDecimal.valueOf(0.01))
                .setScale(4, RoundingMode.HALF_UP);

        price.setCurrentPrice(newPrice);
        price.setVolume(price.getVolume() + random.nextInt(10000));

        if (newPrice.compareTo(price.getHighPrice() == null ? newPrice : price.getHighPrice()) > 0) {
            price.setHighPrice(newPrice);
        }
        if (newPrice.compareTo(price.getLowPrice() == null ? newPrice : price.getLowPrice()) < 0) {
            price.setLowPrice(newPrice);
        }

        TickerPrice saved = tickerPriceRepository.save(price);
        stopLossTriggerService.evaluateTriggers(saved.getTicker(), saved.getCurrentPrice());
        return saved;
    }

    public Map<String, Object> triggerScenario(String scenarioName, String triggeredBy) {
        return scenarioEngine.start(scenarioName, triggeredBy);
    }

    public Map<String, Object> stopScenario() {
        return scenarioEngine.stop();
    }

    public Map<String, Object> getScenarioStatus() {
        return scenarioEngine.getStatus();
    }
}
