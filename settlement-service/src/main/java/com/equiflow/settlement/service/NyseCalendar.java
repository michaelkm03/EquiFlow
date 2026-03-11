package com.equiflow.settlement.service;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

@Component
public class NyseCalendar {

    // 2026 NYSE Holidays (US market holidays)
    private static final Set<LocalDate> HOLIDAYS_2026 = Set.of(
        LocalDate.of(2026, 1, 1),   // New Year's Day
        LocalDate.of(2026, 1, 19),  // Martin Luther King Jr. Day
        LocalDate.of(2026, 2, 16),  // Presidents' Day
        LocalDate.of(2026, 4, 3),   // Good Friday
        LocalDate.of(2026, 5, 25),  // Memorial Day
        LocalDate.of(2026, 7, 3),   // Independence Day (observed)
        LocalDate.of(2026, 9, 7),   // Labor Day
        LocalDate.of(2026, 11, 26), // Thanksgiving Day
        LocalDate.of(2026, 11, 27), // Day after Thanksgiving (early close counts as holiday for T+1)
        LocalDate.of(2026, 12, 25)  // Christmas Day
    );

    // 2025 NYSE Holidays
    private static final Set<LocalDate> HOLIDAYS_2025 = Set.of(
        LocalDate.of(2025, 1, 1),
        LocalDate.of(2025, 1, 20),
        LocalDate.of(2025, 2, 17),
        LocalDate.of(2025, 4, 18),
        LocalDate.of(2025, 5, 26),
        LocalDate.of(2025, 6, 19),
        LocalDate.of(2025, 7, 4),
        LocalDate.of(2025, 9, 1),
        LocalDate.of(2025, 11, 27),
        LocalDate.of(2025, 12, 25)
    );

    /**
     * Returns the T+1 settlement date for a given trade date.
     * Skips weekends and NYSE holidays.
     */
    public LocalDate getSettlementDate(LocalDate tradeDate) {
        LocalDate settlement = tradeDate.plusDays(1);
        while (!isBusinessDay(settlement)) {
            settlement = settlement.plusDays(1);
        }
        return settlement;
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !HOLIDAYS_2026.contains(date) && !HOLIDAYS_2025.contains(date);
    }
}
