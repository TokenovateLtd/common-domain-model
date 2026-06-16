package cdm.product.common.schedule.functions;

import cdm.base.datetime.AdjustableDate;
import cdm.base.datetime.AdjustableOrRelativeDate;
import cdm.base.datetime.CalculationPeriodFrequency;
import cdm.base.datetime.PeriodExtendedEnum;
import cdm.base.datetime.RollConventionEnum;
import cdm.product.common.schedule.CalculationPeriodData;
import cdm.product.common.schedule.CalculationPeriodDates;
import cdm.product.common.schedule.StubPeriodTypeEnum;
import com.rosetta.model.lib.records.Date;
import org.finos.cdm.functions.AbstractFunctionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculationPeriodImplTest extends AbstractFunctionTest {

    @Inject
    CalculationPeriod calculationPeriod;

    @Test
    @DisplayName("Any date within a period — start, middle, or end — returns the same period")
    void shouldReturnSamePeriodForAnyDateWithinIt() {
        Date effectiveDate    = Date.of(2018, 1, 3);
        Date terminationDate  = Date.of(2020, 1, 3);
        CalculationPeriodDates calculationPeriodDates = simpleCalculationPeriodDates(RollConventionEnum._3, 3, PeriodExtendedEnum.M, effectiveDate, terminationDate);

        CalculationPeriodData usingStartDate = calculationPeriod.evaluate(calculationPeriodDates, Date.of(2018, 1, 3));
        CalculationPeriodData usingMidDate   = calculationPeriod.evaluate(calculationPeriodDates, Date.of(2018, 2, 14));
        CalculationPeriodData usingEndDate   = calculationPeriod.evaluate(calculationPeriodDates, Date.of(2018, 3, 31));

        assertEquals(usingStartDate, usingMidDate);
        assertEquals(usingStartDate, usingEndDate);
    }

    @Test
    @DisplayName("Period end-date is inclusive; the day immediately after belongs to the next period")
    void shouldReturnCorrectDaysInPeriodWhenOverlappingPeriods() {
        // roll=27 2M: one period ends 2021-02-27, the next starts 2021-02-28
        Date effectiveDate   = Date.of(2020, 4, 27);
        Date terminationDate = Date.of(2022, 4, 27);
        CalculationPeriodDates calculationPeriodDates = simpleCalculationPeriodDates(RollConventionEnum._27, 2, PeriodExtendedEnum.M, effectiveDate, terminationDate);

        // Feb 26 and Feb 27 are both inside the period ending Feb 27 (same daysInPeriod = 62)
        Date feb26 = Date.of(2021, 2, 26);
        Date feb27 = Date.of(2021, 2, 27);
        Date feb28 = Date.of(2021, 2, 28);

        assertEquals(calculationPeriod.evaluate(calculationPeriodDates, feb26).getEndDate(),
                     calculationPeriod.evaluate(calculationPeriodDates, feb27).getEndDate(),
                     "Feb 26 and Feb 27 should be in the same period");
        assertEquals(feb27, calculationPeriod.evaluate(calculationPeriodDates, feb27).getEndDate(),
                     "Feb 27 should be the last day of its period");
        assertEquals(feb28, calculationPeriod.evaluate(calculationPeriodDates, feb28).getStartDate(),
                     "Feb 28 should be the first day of the next period");
    }

    @Test
    @DisplayName("Termination date is always reachable even when it falls on a Strata period boundary")
    void shouldReturnPeriodForTerminationDateWhenItFormsSingleDayFinalPeriod() {
        // roll=28 applied to terminationDate Mar 1 gives adjustedEnd = Mar 28.
        // Strata periods: [Jan 28, Feb 28], [Feb 28, Mar 28].
        // After adding [Jan 1, Feb 28], currentStart advances to Mar 1 = terminationDate.
        // The last period cannot be [Mar 1, Mar 1] (Strata rejects zero-duration periods),
        // so the previous period is extended: [Jan 1, Feb 28] becomes [Jan 1, Mar 1].
        Date effectiveDate   = Date.of(2025, 1, 1);
        Date terminationDate = Date.of(2025, 3, 1);
        CalculationPeriodDates calculationPeriodDates = simpleCalculationPeriodDates(
                RollConventionEnum._29, 1, PeriodExtendedEnum.M,
                effectiveDate, terminationDate);

        assertCalculationPeriod(
                calculationPeriod.evaluate(calculationPeriodDates, Date.of(2025, 3, 1)),
                expectedPeriod("2025-01-01", "2025-03-01", 60, 0, true, true),
                null
        );
    }

    @ParameterizedTest(name = "withAdjustDate={0}")
    @ValueSource(booleans = {false, true})
    void shouldFindPeriodRegardlessOfDateFieldType(boolean withAdjustDate) {
        Date effectiveDate   = Date.of(2023, 1, 16);
        Date terminationDate = Date.of(2024, 1, 16);
        CalculationPeriodDates calculationPeriodDates = CalculationPeriodDates.builder()
                .setEffectiveDate(adjustableOrRelativeDate(effectiveDate, withAdjustDate))
                .setTerminationDate(adjustableOrRelativeDate(terminationDate, withAdjustDate))
                .setCalculationPeriodFrequency(frequency(RollConventionEnum._16, 1, PeriodExtendedEnum.M))
                .build();

        assertCalculationPeriod(
                calculationPeriod.evaluate(calculationPeriodDates, Date.of(2023, 5, 16)),
                expectedPeriod("2023-04-17", "2023-05-16", 30, 0, false, false),
                null
        );
    }

    private static Stream<Arguments> numericRollScheduleCases() {
        CalculationPeriodDates roll16Monthly = simpleCalculationPeriodDates(
                RollConventionEnum._16, 1, PeriodExtendedEnum.M,
                Date.of(2021, 8, 16), Date.of(2025, 8, 16));

        return Stream.of(
                periodCase("roll=16 1M — first period",
                        roll16Monthly, Date.of(2021, 9, 16),
                        expectedPeriod("2021-08-16", "2021-09-16", 32, 0, true, false)),

                periodCase("roll=16 1M — mid-period",
                        simpleCalculationPeriodDates(RollConventionEnum._16, 1, PeriodExtendedEnum.M,
                                Date.of(2023, 1, 16), Date.of(2024, 1, 16)),
                        Date.of(2023, 5, 16),
                        expectedPeriod("2023-04-17", "2023-05-16", 30, 0, false, false)),

                periodCase("roll=16 1M — mid-period in leap year",
                        roll16Monthly, Date.of(2024, 7, 16),
                        expectedPeriod("2024-06-17", "2024-07-16", 30, 30, false, false)),

                periodCase("roll=16 1M — last period",
                        roll16Monthly, Date.of(2025, 8, 16),
                        expectedPeriod("2025-07-17", "2025-08-16", 31, 0, false, true)),

                periodCase("roll=20 1M — mid-period",
                        simpleCalculationPeriodDates(RollConventionEnum._20, 1, PeriodExtendedEnum.M,
                                Date.of(2023, 1, 20), Date.of(2024, 1, 20)),
                        Date.of(2023, 5, 16),
                        expectedPeriod("2023-04-21", "2023-05-20", 30, 0, false, false)),

                periodCase("roll=1 3M — mid-period in leap year",
                        simpleCalculationPeriodDates(RollConventionEnum._1, 3, PeriodExtendedEnum.M,
                                Date.of(2023, 1, 1), Date.of(2025, 1, 1)),
                        Date.of(2024, 3, 1),
                        expectedPeriod("2024-01-02", "2024-04-01", 91, 91, false, false)),

                periodCase("roll=15 3M — first period",
                        simpleCalculationPeriodDates(RollConventionEnum._15, 3, PeriodExtendedEnum.M,
                                Date.of(2025, 9, 15), Date.of(2026, 9, 15)),
                        Date.of(2025, 11, 15),
                        expectedPeriod("2025-09-15", "2025-12-15", 92, 0, true, false)),

                periodCase("roll=15 6M — mid-period",
                        simpleCalculationPeriodDates(RollConventionEnum._15, 6, PeriodExtendedEnum.M,
                                Date.of(2024, 3, 15), Date.of(2026, 3, 15)),
                        Date.of(2025, 5, 20),
                        expectedPeriod("2025-03-16", "2025-09-15", 184, 0, false, false)),

                periodCase("roll=1 3M — roll day differs from effective date (roll adjusts period boundaries)",
                        simpleCalculationPeriodDates(RollConventionEnum._1, 3, PeriodExtendedEnum.M,
                                Date.of(2018, 1, 3), Date.of(2020, 1, 3)),
                        Date.of(2018, 4, 23),
                        expectedPeriod("2018-04-02", "2018-07-01")),
                periodCase("roll=27 2M — short final stub auto-detected (termination date not on roll day)",
                        simpleCalculationPeriodDates(RollConventionEnum._27, 2, PeriodExtendedEnum.M,
                                Date.of(2023, 4, 27), Date.of(2023, 7, 10)),
                        Date.of(2023, 7, 5),
                        expectedPeriod("2023-06-28", "2023-07-10", 13, 0, false, true))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("numericRollScheduleCases")
    void shouldFindCalculationPeriodForNumericRollSchedule(String name, CalculationPeriodDates calculationPeriodDates, Date target, ExpectedPeriod expected) {
        assertCalculationPeriod(calculationPeriod.evaluate(calculationPeriodDates, target), expected, name);
    }

    private static Stream<Arguments> specialRollConventionCases() {
        return Stream.of(
                periodCase("IMM 1M — mid-period",
                        simpleCalculationPeriodDates(RollConventionEnum.IMM, 1, PeriodExtendedEnum.M,
                                Date.of(2023, 1, 18), Date.of(2024, 1, 17)),
                        Date.of(2023, 5, 16),
                        expectedPeriod("2023-04-20", "2023-05-17", 28, 0, false, false)),

                periodCase("SAT 1W — mid-period",
                        simpleCalculationPeriodDates(RollConventionEnum.SAT, 1, PeriodExtendedEnum.W,
                                Date.of(2025, 10, 18), Date.of(2025, 12, 6)),
                        Date.of(2025, 11, 7),
                        expectedPeriod("2025-11-02", "2025-11-08", 7, 0, false, false)),

                periodCase("EOM 1M — mid-period",
                        simpleCalculationPeriodDates(RollConventionEnum.EOM, 1, PeriodExtendedEnum.M,
                                Date.of(2022, 12, 31), Date.of(2024, 6, 30)),
                        Date.of(2023, 2, 10),
                        expectedPeriod("2023-02-01", "2023-02-28", 28, 0, false, false)),

                periodCase("EOM 3M — leap-year boundary",
                        simpleCalculationPeriodDates(RollConventionEnum.EOM, 3, PeriodExtendedEnum.M,
                                Date.of(2022, 11, 30), Date.of(2025, 2, 28)),
                        Date.of(2024, 2, 10),
                        expectedPeriod("2023-12-01", "2024-02-29", 91, 60, false, false))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("specialRollConventionCases")
    void shouldFindCalculationPeriodForSpecialRollConvention(String name, CalculationPeriodDates calculationPeriodDates, Date target, ExpectedPeriod expected) {
        assertCalculationPeriod(calculationPeriod.evaluate(calculationPeriodDates, target), expected, name);
    }

    private static Stream<Arguments> stubCases() {
        return Stream.of(
                // ── Via firstRegularPeriodStartDate / lastRegularPeriodEndDate ──────────────
                periodCase("SHORT_FINAL (roll=16 1M): stub [Jul 17, Aug 5 2024]",
                        stubCalculationPeriodDates(RollConventionEnum._16, 1, PeriodExtendedEnum.M,
                                Date.of(2021, 8, 16), Date.of(2024, 8, 5),
                                null, Date.of(2024, 7, 16)),
                        Date.of(2024, 8, 1),
                        expectedPeriod("2024-07-17", "2024-08-05", 20, 20, false, true)),

                periodCase("SHORT_INITIAL (roll=16 1M): stub [Aug 25, Sep 16 2021]",
                        stubCalculationPeriodDates(RollConventionEnum._16, 1, PeriodExtendedEnum.M,
                                Date.of(2021, 8, 25), Date.of(2022, 8, 16),
                                Date.of(2021, 9, 16), null),
                        Date.of(2021, 8, 30),
                        expectedPeriod("2021-08-25", "2021-09-16", 23, 0, true, false)),

                periodCase("LONG_FINAL (roll=16 1M): stub [Jun 17, Aug 16 2024]",
                        stubCalculationPeriodDates(RollConventionEnum._16, 1, PeriodExtendedEnum.M,
                                Date.of(2023, 12, 16), Date.of(2024, 8, 16),
                                null, Date.of(2024, 6, 16)),
                        Date.of(2024, 8, 1),
                        expectedPeriod("2024-06-17", "2024-08-16", 61, 61, false, true)),

                periodCase("LONG_INITIAL (roll=16 1M): stub [Aug 16, Oct 16 2021]",
                        stubCalculationPeriodDates(RollConventionEnum._16, 1, PeriodExtendedEnum.M,
                                Date.of(2021, 8, 16), Date.of(2022, 8, 16),
                                Date.of(2021, 10, 16), null),
                        Date.of(2021, 8, 30),
                        expectedPeriod("2021-08-16", "2021-10-16", 62, 0, true, false)),

                periodCase("LONG_FINAL (roll=10 1Y): stub [Aug 11 2025, Aug 12 2026]",
                        stubCalculationPeriodDates(RollConventionEnum._10, 1, PeriodExtendedEnum.Y,
                                Date.of(2022, 8, 10), Date.of(2026, 8, 12),
                                null, Date.of(2025, 8, 10)),
                        Date.of(2025, 11, 12),
                        expectedPeriod("2025-08-11", "2026-08-12", 367, 0, false, true)),

                // ── Via StubPeriodTypeEnum (4M roll=_1) ────────────────────────────────────
                periodCase("SHORT_FINAL stub type — target in final short stub",
                        stubCalculationPeriodDates(RollConventionEnum._1, 4, PeriodExtendedEnum.M,
                                Date.of(2025, 2, 1), Date.of(2025, 11, 1),
                                null, null, StubPeriodTypeEnum.SHORT_FINAL),
                        Date.of(2025, 10, 15),
                        expectedPeriod("2025-10-02", "2025-11-01", 31, 0, false, true)),

                periodCase("LONG_FINAL stub type — target in merged final long stub",
                        stubCalculationPeriodDates(RollConventionEnum._1, 4, PeriodExtendedEnum.M,
                                Date.of(2025, 2, 1), Date.of(2025, 11, 1),
                                null, null, StubPeriodTypeEnum.LONG_FINAL),
                        Date.of(2025, 10, 15),
                        expectedPeriod("2025-06-02", "2025-11-01", 153, 0, false, true)),

                periodCase("SHORT_INITIAL stub type — target in initial short stub",
                        stubCalculationPeriodDates(RollConventionEnum._1, 4, PeriodExtendedEnum.M,
                                Date.of(2025, 1, 1), Date.of(2025, 11, 1),
                                null, null, StubPeriodTypeEnum.SHORT_INITIAL),
                        Date.of(2025, 1, 15),
                        expectedPeriod("2025-01-01", "2025-03-01", 60, 0, true, false)),

                periodCase("LONG_INITIAL stub type — target in merged initial long stub",
                        stubCalculationPeriodDates(RollConventionEnum._1, 4, PeriodExtendedEnum.M,
                                Date.of(2025, 1, 1), Date.of(2025, 11, 1),
                                null, null, StubPeriodTypeEnum.LONG_INITIAL),
                        Date.of(2025, 1, 15),
                        expectedPeriod("2025-01-01", "2025-07-01", 182, 0, true, false)),

                periodCase("SHORT_FINAL stub type combined with lastRegularPeriodEndDate",
                        stubCalculationPeriodDates(RollConventionEnum._1, 4, PeriodExtendedEnum.M,
                                Date.of(2025, 1, 1), Date.of(2025, 8, 15),
                                null, Date.of(2025, 5, 1), StubPeriodTypeEnum.SHORT_FINAL),
                        Date.of(2025, 6, 1),
                        expectedPeriod("2025-05-02", "2025-08-15", 106, 0, false, true)),

                periodCase("SHORT_FINAL stub type — target in a regular (non-stub) period",
                        stubCalculationPeriodDates(RollConventionEnum._1, 4, PeriodExtendedEnum.M,
                                Date.of(2025, 2, 1), Date.of(2025, 11, 1),
                                null, null, StubPeriodTypeEnum.SHORT_FINAL),
                        Date.of(2025, 3, 15),
                        expectedPeriod("2025-02-01", "2025-06-01", 121, 0, true, false)),

                periodCase("SHORT_INITIAL stub type combined with firstRegularPeriodStartDate",
                        stubCalculationPeriodDates(RollConventionEnum._1, 4, PeriodExtendedEnum.M,
                                Date.of(2025, 1, 1), Date.of(2025, 10, 1),
                                Date.of(2025, 2, 1), null, StubPeriodTypeEnum.SHORT_INITIAL),
                        Date.of(2025, 1, 15),
                        expectedPeriod("2025-01-01", "2025-02-01", 32, 0, true, false))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stubCases")
    void shouldFindCalculationPeriodForStub(String name, CalculationPeriodDates calculationPeriodDates, Date target, ExpectedPeriod expected) {
        assertCalculationPeriod(calculationPeriod.evaluate(calculationPeriodDates, target), expected, name);
    }

    @Test
    @DisplayName("When two stub types are set, only the first one is used")
    void shouldUseFirstStubTypeWhenBothInitialAndFinalAreSet() {
        // CDM allows stubPeriodType (0..2) — both initial and final can be set.
        // The implementation reads only stubTypes.get(0), so SHORT_INITIAL wins here.
        Date effectiveDate   = Date.of(2025, 1, 1);
        Date terminationDate = Date.of(2025, 11, 1);
        CalculationPeriodDates calculationPeriodDates = stubCalculationPeriodDates(
                        RollConventionEnum._1, 4, PeriodExtendedEnum.M,
                        effectiveDate, terminationDate,
                        null, null, StubPeriodTypeEnum.SHORT_INITIAL)
                .toBuilder()
                .addStubPeriodType(StubPeriodTypeEnum.SHORT_FINAL) // appended second — ignored
                .build();

        // Result must equal the plain SHORT_INITIAL case: initial stub [Jan 1, Mar 1]
        assertCalculationPeriod(
                calculationPeriod.evaluate(calculationPeriodDates, Date.of(2025, 1, 15)),
                expectedPeriod("2025-01-01", "2025-03-01", 60, 0, true, false),
                null
        );
    }


    @Test
    @DisplayName("Duration shorter than one frequency period produces a single-period schedule")
    void shouldReturnSinglePeriodWhenDurationShorterThanFrequency() {
        // 3M frequency but only ~6 weeks between effective and termination
        Date effectiveDate   = Date.of(2025, 1, 1);
        Date terminationDate = Date.of(2025, 2, 15);
        CalculationPeriodDates calculationPeriodDates = simpleCalculationPeriodDates(
                RollConventionEnum._1, 3, PeriodExtendedEnum.M,
                effectiveDate, terminationDate);

        assertCalculationPeriod(
                calculationPeriod.evaluate(calculationPeriodDates, Date.of(2025, 1, 20)),
                expectedPeriod("2025-01-01", "2025-02-15", 46, 0, true, true),
                null
        );
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "target before effective date, 2024-12-31",
            "target after termination date, 2027-08-16"
    })
    void shouldReturnEmptyForOutOfRangeDate(String name, String targetDate) {
        Date effectiveDate   = Date.of(2025, 1, 1);
        Date terminationDate = Date.of(2027, 1, 1);
        CalculationPeriodDates calculationPeriodDates = simpleCalculationPeriodDates(
                RollConventionEnum._1, 4, PeriodExtendedEnum.M,
                effectiveDate, terminationDate);
        CalculationPeriodData result = calculationPeriod.evaluate(calculationPeriodDates, parseDate(targetDate));

        assertNotNull(result, name);
        assertNull(result.getStartDate(), name + ": startDate should be null");
    }

    @Test
    void shouldThrowWhenEffectiveAfterTermination() {
        Date effectiveDate   = Date.of(2027, 12, 1);
        Date terminationDate = Date.of(2025, 8, 15);
        CalculationPeriodDates calculationPeriodDates = simpleCalculationPeriodDates(
                RollConventionEnum._1, 4, PeriodExtendedEnum.M,
                effectiveDate, terminationDate);

        assertThrows(IllegalArgumentException.class,
                () -> calculationPeriod.evaluate(calculationPeriodDates, Date.of(2025, 12, 15)));
    }

    @Test
    void shouldThrowWhenCalculationPeriodDatesIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> calculationPeriod.evaluate(null, Date.of(2025, 6, 1)));
    }

    @Test
    void shouldThrowWhenTargetDateIsNull() {
        Date effectiveDate   = Date.of(2025, 1, 1);
        Date terminationDate = Date.of(2026, 1, 1);
        CalculationPeriodDates calculationPeriodDates = simpleCalculationPeriodDates(
                RollConventionEnum._1, 1, PeriodExtendedEnum.M,
                effectiveDate, terminationDate);
        assertThrows(IllegalArgumentException.class,
                () -> calculationPeriod.evaluate(calculationPeriodDates, null));
    }

    @Test
    void shouldThrowWhenCalculationPeriodFrequencyIsNull() {
        Date effectiveDate   = Date.of(2025, 1, 1);
        Date terminationDate = Date.of(2026, 1, 1);
        CalculationPeriodDates calculationPeriodDates = CalculationPeriodDates.builder()
                .setEffectiveDate(adjustableOrRelativeDate(effectiveDate))
                .setTerminationDate(adjustableOrRelativeDate(terminationDate))
                .build(); // no frequency set
        assertThrows(IllegalArgumentException.class,
                () -> calculationPeriod.evaluate(calculationPeriodDates, Date.of(2025, 6, 1)));
    }

    @Test
    void shouldThrowWhenPeriodIsNull() {
        Date effectiveDate   = Date.of(2025, 1, 1);
        Date terminationDate = Date.of(2026, 1, 1);
        CalculationPeriodDates calculationPeriodDates = CalculationPeriodDates.builder()
                .setEffectiveDate(adjustableOrRelativeDate(effectiveDate))
                .setTerminationDate(adjustableOrRelativeDate(terminationDate))
                .setCalculationPeriodFrequency(CalculationPeriodFrequency.builder()
                        .setRollConvention(RollConventionEnum._1)
                        .setPeriodMultiplier(1)
                        .build()) // period intentionally omitted
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> calculationPeriod.evaluate(calculationPeriodDates, Date.of(2025, 6, 1)));
    }

    @Test
    void shouldThrowWhenPeriodMultiplierIsNull() {
        Date effectiveDate   = Date.of(2025, 1, 1);
        Date terminationDate = Date.of(2026, 1, 1);
        CalculationPeriodDates calculationPeriodDates = CalculationPeriodDates.builder()
                .setEffectiveDate(adjustableOrRelativeDate(effectiveDate))
                .setTerminationDate(adjustableOrRelativeDate(terminationDate))
                .setCalculationPeriodFrequency(CalculationPeriodFrequency.builder()
                        .setRollConvention(RollConventionEnum._1)
                        .setPeriod(PeriodExtendedEnum.M)
                        .build()) // periodMultiplier intentionally omitted
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> calculationPeriod.evaluate(calculationPeriodDates, Date.of(2025, 6, 1)));
    }

    @Test
    void shouldThrowWhenEffectiveDateIsNull() {
        // Tests first checkNotNull in validateDateField: getEffectiveDate() itself returns null
        Date terminationDate = Date.of(2026, 1, 1);
        CalculationPeriodDates calculationPeriodDates = CalculationPeriodDates.builder()
                // effectiveDate intentionally omitted
                .setTerminationDate(adjustableOrRelativeDate(terminationDate))
                .setCalculationPeriodFrequency(frequency(RollConventionEnum._1, 1, PeriodExtendedEnum.M))
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> calculationPeriod.evaluate(calculationPeriodDates, Date.of(2025, 6, 1)));
    }

    @Test
    void shouldThrowWhenRollConventionIsNull() {
        Date effectiveDate   = Date.of(2025, 1, 1);
        Date terminationDate = Date.of(2026, 1, 1);
        CalculationPeriodDates calculationPeriodDates = CalculationPeriodDates.builder()
                .setEffectiveDate(adjustableOrRelativeDate(effectiveDate))
                .setTerminationDate(adjustableOrRelativeDate(terminationDate))
                .setCalculationPeriodFrequency(CalculationPeriodFrequency.builder()
                        .setPeriodMultiplier(1)
                        .setPeriod(PeriodExtendedEnum.M)
                        .build()) // rollConvention intentionally omitted
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> calculationPeriod.evaluate(calculationPeriodDates, Date.of(2025, 6, 1)));
    }

    @Test
    void shouldThrowWhenAdjustableDateIsNull() {
        // AdjustableOrRelativeDate exists but has no AdjustableDate inside it
        Date terminationDate = Date.of(2026, 1, 1);
        AdjustableOrRelativeDate aord = AdjustableOrRelativeDate.builder().build();
        CalculationPeriodDates calculationPeriodDates = CalculationPeriodDates.builder()
                .setEffectiveDate(aord)
                .setTerminationDate(adjustableOrRelativeDate(terminationDate))
                .setCalculationPeriodFrequency(frequency(RollConventionEnum._1, 1, PeriodExtendedEnum.M))
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> calculationPeriod.evaluate(calculationPeriodDates, Date.of(2025, 6, 1)));
    }

    @Test
    void shouldThrowWhenBothUnadjustedAndAdjustedDateSet() {
        Date effectiveDate   = Date.of(2025, 1, 1);
        Date terminationDate = Date.of(2026, 1, 1);
        AdjustableDate adjustableDate = AdjustableDate.builder()
                .setUnadjustedDate(effectiveDate)
                .setAdjustedDateValue(effectiveDate) // both fields set — invalid
                .build();
        CalculationPeriodDates calculationPeriodDates = CalculationPeriodDates.builder()
                .setEffectiveDate(AdjustableOrRelativeDate.builder().setAdjustableDate(adjustableDate).build())
                .setTerminationDate(adjustableOrRelativeDate(terminationDate))
                .setCalculationPeriodFrequency(frequency(RollConventionEnum._1, 1, PeriodExtendedEnum.M))
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> calculationPeriod.evaluate(calculationPeriodDates, Date.of(2025, 6, 1)));
    }

    @Test
    void shouldThrowWhenNeitherUnadjustedNorAdjustedDateSet() {
        Date terminationDate = Date.of(2026, 1, 1);
        AdjustableDate adjustableDate = AdjustableDate.builder().build(); // no date value at all
        CalculationPeriodDates calculationPeriodDates = CalculationPeriodDates.builder()
                .setEffectiveDate(AdjustableOrRelativeDate.builder().setAdjustableDate(adjustableDate).build())
                .setTerminationDate(adjustableOrRelativeDate(terminationDate))
                .setCalculationPeriodFrequency(frequency(RollConventionEnum._1, 1, PeriodExtendedEnum.M))
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> calculationPeriod.evaluate(calculationPeriodDates, Date.of(2025, 6, 1)));
    }

    private void assertCalculationPeriod(CalculationPeriodData result, ExpectedPeriod expected, String messagePrefix) {
        String prefix = messagePrefix == null ? "" : messagePrefix + ": ";

        assertNotNull(result, messagePrefix);
        assertEquals(expected.startDate, result.getStartDate().toString(), prefix + "startDate");
        assertEquals(expected.endDate, result.getEndDate().toString(), prefix + "endDate");

        if (expected.daysInPeriod != null) {
            assertEquals(expected.daysInPeriod, result.getDaysInPeriod(), prefix + "daysInPeriod");
        }
        if (expected.daysInLeapYearPeriod != null) {
            assertEquals(expected.daysInLeapYearPeriod, result.getDaysInLeapYearPeriod(), prefix + "daysInLeapYearPeriod");
        }
        if (expected.isFirstPeriod != null) {
            assertEquals(expected.isFirstPeriod, result.getIsFirstPeriod(), prefix + "isFirstPeriod");
        }
        if (expected.isLastPeriod != null) {
            assertEquals(expected.isLastPeriod, result.getIsLastPeriod(), prefix + "isLastPeriod");
        }
    }

    private static CalculationPeriodDates simpleCalculationPeriodDates(
            RollConventionEnum rollConvention,
            int periodMultiplier,
            PeriodExtendedEnum period,
            Date effectiveDate,
            Date terminationDate) {
        return CalculationPeriodDates.builder()
                .setEffectiveDate(adjustableOrRelativeDate(effectiveDate))
                .setTerminationDate(adjustableOrRelativeDate(terminationDate))
                .setCalculationPeriodFrequency(frequency(rollConvention, periodMultiplier, period))
                .build();
    }

    private static CalculationPeriodDates stubCalculationPeriodDates(
            RollConventionEnum rollConvention,
            int periodMultiplier,
            PeriodExtendedEnum period,
            Date effectiveDate,
            Date terminationDate,
            Date firstRegularPeriodStartDate,
            Date lastRegularPeriodEndDate) {
        return stubCalculationPeriodDates(
                rollConvention, periodMultiplier, period,
                effectiveDate, terminationDate,
                firstRegularPeriodStartDate, lastRegularPeriodEndDate,
                null);
    }

    private static CalculationPeriodDates stubCalculationPeriodDates(
            RollConventionEnum rollConvention,
            int periodMultiplier,
            PeriodExtendedEnum period,
            Date effectiveDate,
            Date terminationDate,
            Date firstRegularPeriodStartDate,
            Date lastRegularPeriodEndDate,
            StubPeriodTypeEnum stubPeriodType) {
        CalculationPeriodDates.CalculationPeriodDatesBuilder builder = simpleCalculationPeriodDates(
                rollConvention, periodMultiplier, period, effectiveDate, terminationDate).toBuilder();

        if (firstRegularPeriodStartDate != null) {
            builder.setFirstRegularPeriodStartDate(firstRegularPeriodStartDate);
        }
        if (lastRegularPeriodEndDate != null) {
            builder.setLastRegularPeriodEndDate(lastRegularPeriodEndDate);
        }
        if (stubPeriodType != null) {
            builder.addStubPeriodType(stubPeriodType);
        }
        return builder.build();
    }

    private static CalculationPeriodFrequency frequency(
            RollConventionEnum rollConvention,
            int periodMultiplier,
            PeriodExtendedEnum period) {
        return CalculationPeriodFrequency.builder()
                .setRollConvention(rollConvention)
                .setPeriodMultiplier(periodMultiplier)
                .setPeriod(period)
                .build();
    }

    private static AdjustableOrRelativeDate adjustableOrRelativeDate(Date date) {
        return adjustableOrRelativeDate(date, false);
    }

    private static AdjustableOrRelativeDate adjustableOrRelativeDate(Date date, boolean withAdjustedDate) {
        AdjustableDate.AdjustableDateBuilder builder = AdjustableDate.builder();
        if (withAdjustedDate) {
            builder.setAdjustedDateValue(date);
        } else {
            builder.setUnadjustedDate(date);
        }
        return AdjustableOrRelativeDate.builder().setAdjustableDate(builder.build()).build();
    }

    private static Date parseDate(String s) {
        String[] parts = s.split("-");
        return Date.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private static Arguments periodCase(String name, CalculationPeriodDates calculationPeriodDates, Date target, ExpectedPeriod expected) {
        return Arguments.of(name, calculationPeriodDates, target, expected);
    }

    private static ExpectedPeriod expectedPeriod(String startDate, String endDate) {
        return new ExpectedPeriod(startDate, endDate, null, null, null, null);
    }

    private static ExpectedPeriod expectedPeriod(
            String startDate,
            String endDate,
            int daysInPeriod,
            int daysInLeapYearPeriod,
            boolean isFirstPeriod,
            boolean isLastPeriod) {
        return new ExpectedPeriod(startDate, endDate, daysInPeriod, daysInLeapYearPeriod, isFirstPeriod, isLastPeriod);
    }

    private static final class ExpectedPeriod {
        private final String startDate;
        private final String endDate;
        private final Integer daysInPeriod;
        private final Integer daysInLeapYearPeriod;
        private final Boolean isFirstPeriod;
        private final Boolean isLastPeriod;

        private ExpectedPeriod(
                String startDate,
                String endDate,
                Integer daysInPeriod,
                Integer daysInLeapYearPeriod,
                Boolean isFirstPeriod,
                Boolean isLastPeriod) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.daysInPeriod = daysInPeriod;
            this.daysInLeapYearPeriod = daysInLeapYearPeriod;
            this.isFirstPeriod = isFirstPeriod;
            this.isLastPeriod = isLastPeriod;
        }
    }
}
