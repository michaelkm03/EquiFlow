package com.equiflow.settlement;

import com.equiflow.settlement.service.NyseCalendar;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.time.LocalDate;

import static org.testng.Assert.*;

public class SettlementServiceTest {

    private NyseCalendar nyseCalendar;

    @BeforeMethod
    public void setUp() {
        nyseCalendar = new NyseCalendar();
    }

    @DataProvider(name = "settlementDates")
    public Object[][] settlementDatesProvider() {
        return new Object[][] {
            // Trade date, expected settlement date
            // Monday -> Tuesday (both business days)
            {LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3)},
            // Friday -> Monday (skip weekend)
            {LocalDate.of(2026, 3, 6), LocalDate.of(2026, 3, 9)},
            // Thursday before Good Friday (Apr 2 -> Apr 6, skip Apr 3 holiday)
            {LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 6)},
            // Day before NYE (Dec 24) -> Dec 28 (skip Christmas Dec 25, weekend Dec 26-27)
            {LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 28)},
        };
    }

    @Test(dataProvider = "settlementDates",
          description = "T+1 settlement skips weekends and NYSE holidays")
    public void testT1BusinessDayCalculation(LocalDate tradeDate, LocalDate expectedSettlement) {
        LocalDate actual = nyseCalendar.getSettlementDate(tradeDate);
        assertEquals(actual, expectedSettlement,
                String.format("Trade date %s should settle on %s, got %s",
                        tradeDate, expectedSettlement, actual));
    }

    @Test(description = "Saturday is not a business day")
    public void testSaturdayIsNotBusinessDay() {
        LocalDate saturday = LocalDate.of(2026, 3, 7);
        assertFalse(nyseCalendar.isBusinessDay(saturday), "Saturday should not be a business day");
    }

    @Test(description = "Sunday is not a business day")
    public void testSundayIsNotBusinessDay() {
        LocalDate sunday = LocalDate.of(2026, 3, 8);
        assertFalse(nyseCalendar.isBusinessDay(sunday), "Sunday should not be a business day");
    }

    @Test(description = "NYSE holiday is not a business day")
    public void testHolidayIsNotBusinessDay() {
        LocalDate goodFriday2026 = LocalDate.of(2026, 4, 3);
        assertFalse(nyseCalendar.isBusinessDay(goodFriday2026),
                "Good Friday 2026 should not be a business day");
    }

    @Test(description = "Regular Wednesday is a business day")
    public void testWednesdayIsBusinessDay() {
        LocalDate wednesday = LocalDate.of(2026, 3, 4);
        assertTrue(nyseCalendar.isBusinessDay(wednesday), "Wednesday should be a business day");
    }

    @Test(description = "Settlement from Friday skips to Monday")
    public void testFridaySettlesToMonday() {
        LocalDate friday = LocalDate.of(2026, 3, 6);
        LocalDate settlement = nyseCalendar.getSettlementDate(friday);
        assertEquals(settlement.getDayOfWeek().getValue(), 1,
                "Settlement from Friday should be Monday");
    }
}
