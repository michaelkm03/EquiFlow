package com.equiflow.market.service;

import com.equiflow.market.model.ScenarioEvent;
import com.equiflow.market.model.TickerPrice;
import com.equiflow.market.repository.ScenarioEventRepository;
import com.equiflow.market.repository.TickerPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScenarioEngine {

    private final TickerPriceRepository tickerPriceRepository;
    private final ScenarioEventRepository scenarioEventRepository;

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicReference<String> activeScenario = new AtomicReference<>("");
    private final AtomicInteger stepCounter = new AtomicInteger(0);
    private volatile Instant startedAt;

    // Scenario definitions: name -> array of [ticker, priceDeltaPct]
    private static final Map<String, double[][]> SCENARIOS = Map.of(
        "flash_crash", new double[][] {
            {-0.05}, {-0.08}, {-0.12}, {-0.15}, {0.10}, {0.07}
        },
        "bull_run", new double[][] {
            {0.02}, {0.03}, {0.05}, {0.04}, {0.06}, {0.03}
        },
        "bear_market", new double[][] {
            {-0.01}, {-0.02}, {-0.015}, {-0.025}, {-0.03}, {-0.02}
        },
        "high_volatility", new double[][] {
            {0.04}, {-0.05}, {0.07}, {-0.06}, {0.09}, {-0.08}
        },
        "sector_rotation", new double[][] {
            {0.03}, {0.02}, {-0.01}, {0.04}, {0.01}, {0.02}
        },
        "liquidity_crisis", new double[][] {
            {-0.03}, {-0.07}, {-0.10}, {-0.08}, {-0.05}, {-0.03}
        }
    );

    public Map<String, Object> start(String scenarioName, String triggeredBy) {
        if (!SCENARIOS.containsKey(scenarioName)) {
            throw new IllegalArgumentException("Unknown scenario: " + scenarioName +
                    ". Available: " + SCENARIOS.keySet());
        }

        if (active.get()) {
            stop();
        }

        active.set(true);
        activeScenario.set(scenarioName);
        stepCounter.set(0);
        startedAt = Instant.now();

        scenarioEventRepository.save(ScenarioEvent.builder()
                .scenarioName(scenarioName)
                .action("STARTED")
                .description("Scenario started: " + scenarioName)
                .triggeredBy(triggeredBy)
                .build());

        log.info("Scenario '{}' started by {}", scenarioName, triggeredBy);

        return getStatus();
    }

    public Map<String, Object> stop() {
        String name = activeScenario.get();
        active.set(false);
        activeScenario.set("");
        stepCounter.set(0);

        if (!name.isEmpty()) {
            scenarioEventRepository.save(ScenarioEvent.builder()
                    .scenarioName(name)
                    .action("STOPPED")
                    .description("Scenario stopped")
                    .triggeredBy("system")
                    .build());
            log.info("Scenario '{}' stopped", name);
        }

        return getStatus();
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("active", active.get());
        status.put("scenario", activeScenario.get());
        status.put("step", stepCounter.get());
        status.put("startedAt", startedAt);
        status.put("availableScenarios", SCENARIOS.keySet());
        return status;
    }

    @Scheduled(fixedDelayString = "${market.scenario.step-interval-ms:5000}")
    @Transactional
    public void applyNextStep() {
        if (!active.get()) return;

        String scenario = activeScenario.get();
        double[][] steps = SCENARIOS.get(scenario);
        if (steps == null) return;

        int step = stepCounter.getAndIncrement();
        if (step >= steps.length) {
            log.info("Scenario '{}' completed all steps, stopping", scenario);
            stop();
            return;
        }

        double deltaPct = steps[step][0];

        // Apply to all tickers
        tickerPriceRepository.findAll().forEach(price -> {
            BigDecimal delta = price.getCurrentPrice()
                    .multiply(BigDecimal.valueOf(deltaPct))
                    .setScale(4, RoundingMode.HALF_UP);
            BigDecimal newPrice = price.getCurrentPrice().add(delta)
                    .max(BigDecimal.valueOf(0.01))
                    .setScale(4, RoundingMode.HALF_UP);
            price.setCurrentPrice(newPrice);
            tickerPriceRepository.save(price);
        });

        scenarioEventRepository.save(ScenarioEvent.builder()
                .scenarioName(scenario)
                .action("STEP_APPLIED")
                .description(String.format("Step %d applied: %.2f%% price change", step + 1, deltaPct * 100))
                .triggeredBy("scheduler")
                .build());

        log.info("Scenario '{}' step {} applied: {}%", scenario, step + 1, deltaPct * 100);
    }
}
