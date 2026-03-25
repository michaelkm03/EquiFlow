package com.equiflow.order.matching;

import com.equiflow.order.kafka.OrderEventPublisher;
import com.equiflow.order.model.Order;
import com.equiflow.order.model.enums.OrderStatus;
import com.equiflow.order.model.enums.OrderType;
import com.equiflow.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingEngine {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    private OrderBook getOrCreateBook(String ticker) {
        return orderBooks.computeIfAbsent(ticker, OrderBook::new);
    }

    @Transactional
    public Order executeMarketOrder(Order order) {
        log.info("Executing market order: {} {} {} shares of {}",
                order.getId(), order.getSide(), order.getQuantity(), order.getTicker());

        OrderBook book = getOrCreateBook(order.getTicker());
        List<OrderBook.MatchResult> matches = book.matchMarketOrder(order);

        return processMatches(order, matches);
    }

    @Transactional
    public Order executeLimitOrder(Order order) {
        log.info("Executing limit order: {} {} {} shares of {} @ {}",
                order.getId(), order.getSide(), order.getQuantity(), order.getTicker(), order.getLimitPrice());

        OrderBook book = getOrCreateBook(order.getTicker());
        List<OrderBook.MatchResult> matches = book.matchLimitOrder(order);

        Order result = processMatches(order, matches);

        // If not fully filled, add to the book
        if (result.getStatus() == OrderStatus.OPEN || result.getStatus() == OrderStatus.PARTIALLY_FILLED) {
            if (result.getSide().name().equals("BUY")) {
                book.addBid(result);
            } else {
                book.addAsk(result);
            }
        }

        return result;
    }

    private Order processMatches(Order order, List<OrderBook.MatchResult> matches) {
        if (matches.isEmpty()) {
            if (order.getType() == OrderType.MARKET || order.getType() == OrderType.STOP_LOSS) {
                // Market and triggered stop-loss orders with no liquidity - reject
                // todo is this correct? should stop-loss orders be rejected if no liquidity, or just remain pending until they can be filled?
                order.setStatus(OrderStatus.REJECTED);
                order.setRejectionReason("No liquidity available for market order");
                log.warn("{} order {} rejected: no liquidity", order.getType(), order.getId());
            } else {
                order.setStatus(OrderStatus.OPEN);
            }
            return orderRepository.save(order);
        }

        BigDecimal totalFilled = BigDecimal.ZERO;
        BigDecimal weightedPriceSum = BigDecimal.ZERO;

        for (OrderBook.MatchResult match : matches) {
            totalFilled = totalFilled.add(match.matchedQty());
            weightedPriceSum = weightedPriceSum.add(
                    match.price().multiply(match.matchedQty()));

            // Update resting order
            Order resting = match.resting();
            BigDecimal restingFilled = (resting.getFilledQty() == null ? BigDecimal.ZERO : resting.getFilledQty())
                    .add(match.matchedQty());
            resting.setFilledQty(restingFilled);
            resting.setFilledPrice(match.price());

            if (restingFilled.compareTo(resting.getQuantity()) >= 0) {
                resting.setStatus(OrderStatus.FILLED);
            } else {
                resting.setStatus(OrderStatus.PARTIALLY_FILLED);
            }
            orderRepository.save(resting);
            eventPublisher.publishOrderFilled(resting);
        }

        BigDecimal avgPrice = weightedPriceSum.divide(totalFilled, 4, RoundingMode.HALF_UP);
        order.setFilledQty(totalFilled);
        order.setFilledPrice(avgPrice);

        if (totalFilled.compareTo(order.getQuantity()) >= 0) {
            order.setStatus(OrderStatus.FILLED);
        } else {
            order.setStatus(OrderStatus.PARTIALLY_FILLED);
        }

        Order saved = orderRepository.save(order);
        eventPublisher.publishOrderFilled(saved);
        return saved;
    }

    public Map<String, Object> getBookSnapshot(String ticker) {
        return getOrCreateBook(ticker).getSnapshot();
    }
}
