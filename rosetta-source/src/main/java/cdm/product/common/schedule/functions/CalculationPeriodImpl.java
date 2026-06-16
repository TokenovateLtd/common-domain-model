package cdm.product.common.schedule.functions;

import cdm.base.datetime.AdjustableDate;
import cdm.base.datetime.AdjustableOrRelativeDate;
import cdm.base.datetime.CalculationPeriodFrequency;
import cdm.base.datetime.RollConventionEnum;
import cdm.product.common.schedule.CalculationPeriodData;
import cdm.product.common.schedule.CalculationPeriodData.CalculationPeriodDataBuilder;
import cdm.product.common.schedule.CalculationPeriodDates;
import cdm.product.common.schedule.StubPeriodTypeEnum;
import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.schedule.RollConvention;
import com.opengamma.strata.basics.schedule.Schedule;
import com.opengamma.strata.basics.schedule.SchedulePeriod;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.rosetta.model.lib.records.Date;
import com.rosetta.model.metafields.FieldWithMetaDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.Period;
import java.time.chrono.IsoChronology;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implements calculation period evaluation logic for a given date within a schedule.
 *
 * <p>This class computes the specific {@link CalculationPeriodData} for a target date based on the
 * {@link CalculationPeriodDates} definition. It handles roll conventions, frequency parsing, stub
 * periods, and leap year adjustments.
 */
public class CalculationPeriodImpl extends CalculationPeriod {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalculationPeriodImpl.class);

    private final AdjustDateToRollConvention adjustDateToRollConvention = new AdjustDateToRollConvention();

    @Override
    protected CalculationPeriodDataBuilder doEvaluate(CalculationPeriodDates calculationPeriodDates, Date date) {
        validateInput(calculationPeriodDates, date);
        LOGGER.debug("Evaluating calculation period for date: {}", date);

        LocalDate effectiveDate = extractDate(calculationPeriodDates.getEffectiveDate());
        LocalDate terminationDate = extractDate(calculationPeriodDates.getTerminationDate());

        validateDateRange(effectiveDate, terminationDate);
        if (isTargetDateOutOfRange(date, effectiveDate, terminationDate)) {
            LOGGER.warn("Date {} is out of schedule range [{}, {}]", date, effectiveDate, terminationDate);
            return CalculationPeriodData.builder();
        }

        // Adjust effective and termination dates according to roll convention to generate schedule.
        // Note that the final periods should still align with the extracted effective and termination dates.
        RollConventionEnum rollConvention = calculationPeriodDates.getCalculationPeriodFrequency().getRollConvention();
        LocalDate adjustedEffectiveDate =
                adjustDateToRollConvention.evaluate(Date.of(effectiveDate), rollConvention).toLocalDate();
        LocalDate adjustedTerminationDate =
                adjustDateToRollConvention.evaluate(Date.of(terminationDate), rollConvention).toLocalDate();

        Schedule schedule = getSchedule(calculationPeriodDates, adjustedEffectiveDate, adjustedTerminationDate);
        List<SchedulePeriod> consecutivePeriods = adjustPeriods(schedule.getPeriods(), effectiveDate, terminationDate);

        return findPeriodContainingDate(consecutivePeriods, date, effectiveDate, terminationDate);
    }

    private void validateInput(CalculationPeriodDates calculationPeriodDates, Date date) {
        checkNotNull(calculationPeriodDates, "calculationPeriodDates");
        checkNotNull(date, "date");

        CalculationPeriodFrequency freq =
                checkNotNull(calculationPeriodDates.getCalculationPeriodFrequency(), "calculationPeriodFrequency");
        checkNotNull(freq.getPeriod(), "calculationPeriodFrequency.period");
        checkNotNull(freq.getPeriodMultiplier(), "calculationPeriodFrequency.periodMultiplier");
        checkNotNull(freq.getRollConvention(), "calculationPeriodFrequency.rollConvention");

        validateDateField(calculationPeriodDates.getEffectiveDate(), "effectiveDate");
        validateDateField(calculationPeriodDates.getTerminationDate(), "terminationDate");
    }

    private void validateDateField(AdjustableOrRelativeDate adjustableOrRelativeDate, String fieldName) {
        checkNotNull(adjustableOrRelativeDate, fieldName);
        AdjustableDate adjustableDate =
                checkNotNull(adjustableOrRelativeDate.getAdjustableDate(), fieldName + ".adjustableDate");

        Date unadjusted = adjustableDate.getUnadjustedDate();
        Date adjusted =
                Optional.ofNullable(adjustableDate.getAdjustedDate())
                        .map(FieldWithMetaDate::getValue)
                        .orElse(null);

        if ((unadjusted == null && adjusted == null) || (unadjusted != null && adjusted != null)) {
            throw new IllegalArgumentException(
                    String.format("%s must have exactly either unadjustedDate or adjustedDate", fieldName));
        }
    }

    private LocalDate extractDate(AdjustableOrRelativeDate adjustableOrRelativeDate) {
        AdjustableDate adjustableDate = adjustableOrRelativeDate.getAdjustableDate();
        Date date = adjustableDate.getUnadjustedDate();
        if (date == null) {
            date = adjustableDate.getAdjustedDate().getValue();
        }
        return date.toLocalDate();
    }

    private void validateDateRange(LocalDate effectiveDate, LocalDate terminationDate) {
        if (effectiveDate.isAfter(terminationDate)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Effective date must be before termination date: effective=%s, termination=%s",
                            effectiveDate, terminationDate));
        }
    }

    private boolean isTargetDateOutOfRange(Date date, LocalDate effectiveDate, LocalDate terminationDate) {
        LocalDate targetDate = date.toLocalDate();
        return targetDate.isBefore(effectiveDate) || targetDate.isAfter(terminationDate);
    }

    private Schedule getSchedule(
            CalculationPeriodDates calculationPeriodDates,
            LocalDate adjustedStartDate,
            LocalDate adjustedEndDate) {

        List<StubPeriodTypeEnum> stubTypes = calculationPeriodDates.getStubPeriodType();
        StubPeriodTypeEnum stubPeriodTypeEnum = (stubTypes != null && !stubTypes.isEmpty()) ? stubTypes.get(0) : null;

        LocalDate firstRegularStartDate = Optional.ofNullable(calculationPeriodDates.getFirstRegularPeriodStartDate())
                .map(Date::toLocalDate).orElse(null);
        LocalDate lastRegularEndDate = Optional.ofNullable(calculationPeriodDates.getLastRegularPeriodEndDate())
                .map(Date::toLocalDate).orElse(null);

        Frequency frequency = CdmToStrataMapper.getFrequency(calculationPeriodDates);
        RollConvention rollConvention = CdmToStrataMapper.getRollConvention(calculationPeriodDates);
        if (rollConvention == null) {
            throw new IllegalArgumentException(
                    "Unknown roll convention: "
                            + calculationPeriodDates.getCalculationPeriodFrequency().getRollConvention());
        }

        StubConvention stubConvention = null;
        if (stubPeriodTypeEnum == null && firstRegularStartDate == null && lastRegularEndDate == null) {
            stubConvention = detectSmartStub(adjustedStartDate, adjustedEndDate, frequency);
        } else if (stubPeriodTypeEnum != null) {
            stubConvention = StubConvention.of(stubPeriodTypeEnum.toString());
        }

        PeriodicSchedule periodicSchedule =
                PeriodicSchedule.builder()
                        .startDate(adjustedStartDate)
                        .endDate(adjustedEndDate)
                        .frequency(frequency)
                        .businessDayAdjustment(BusinessDayAdjustment.NONE)
                        .stubConvention(stubConvention)
                        .rollConvention(rollConvention)
                        .lastRegularEndDate(lastRegularEndDate)
                        .firstRegularStartDate(firstRegularStartDate)
                        .build();

        return periodicSchedule.createSchedule(ReferenceData.minimal());
    }

    private static StubConvention detectSmartStub(LocalDate start, LocalDate end, Frequency frequency) {
        Period freqPeriod = frequency.getPeriod();

        // Forward: collect all regular period end dates
        List<LocalDate> regularEnds = new ArrayList<>();
        LocalDate cur = start.plus(freqPeriod);
        while (!cur.isAfter(end)) {
            regularEnds.add(cur);
            cur = cur.plus(freqPeriod);
        }

        // No regular periods --> the whole range is a single short stub.
        // When regularEnds is empty, end < start+freqPeriod (by the loop condition), so the stub
        // duration is always shorter than one full period — SHORT_FINAL.
        if (regularEnds.isEmpty()) {
            return StubConvention.SHORT_FINAL;
        }

        // Determine normal period length from last regular step
        LocalDate lastRegularEnd = regularEnds.get(regularEnds.size() - 1);
        long normalPeriodDays = regularEnds.size() >= 2
                ? ChronoUnit.DAYS.between(regularEnds.get(regularEnds.size() - 2), lastRegularEnd)
                : ChronoUnit.DAYS.between(start, lastRegularEnd);

        // Tail after last regular period — detect final stub
        long tailDays = ChronoUnit.DAYS.between(lastRegularEnd, end);
        if (tailDays <= 0) {
            return StubConvention.NONE;
        }
        return tailDays < normalPeriodDays ? StubConvention.SHORT_FINAL : StubConvention.LONG_FINAL;
    }

    // ISDA conventions require non-overlapping periods where each period starts the day after the
    // previous ends.Strata generates overlapping periods (boundary dates shared), so we adjust them
    // to meet ISDA requirements. There is also an adjustment on the start date and end date of the
    // first and last period in order to align with the effective date and the termination date.
    private List<SchedulePeriod> adjustPeriods(
            ImmutableList<SchedulePeriod> periods, LocalDate effectiveDate, LocalDate terminationDate) {
        List<SchedulePeriod> consecutivePeriods = new ArrayList<>(periods.size());
        LocalDate currentStart = effectiveDate;

        for (int i = 0; i < periods.size(); i++) {
            boolean isLast = i == periods.size() - 1;

            LocalDate currentEnd = periods.get(i).getEndDate();

            // Adjust to termination:
            //  - the last period must end at terminationDate
            //  - or any overshoot should be brought back to terminationDate
            if (currentEnd.isAfter(terminationDate) || isLast) {
                currentEnd = terminationDate;
            }

            // Edge case: terminationDate falls exactly on the day after a Strata period boundary.
            // currentStart has been advanced to terminationDate but Strata rejects zero-duration
            // periods, so we extend the previously added period to cover the termination date.
            if (currentEnd.equals(currentStart)) {
                SchedulePeriod last = consecutivePeriods.remove(consecutivePeriods.size() - 1);
                consecutivePeriods.add(SchedulePeriod.of(last.getStartDate(), terminationDate));
                break;
            }

            // Create a non-overlapping period [currentStart, currentEnd]
            consecutivePeriods.add(SchedulePeriod.of(currentStart, currentEnd));

            // If termination is reached, done
            if (currentEnd.equals(terminationDate)) {
                break;
            }

            // Next period starts the day after this one ends (ISDA requirement)
            currentStart = currentEnd.plusDays(1);
        }


        return consecutivePeriods;
    }

    private CalculationPeriodDataBuilder findPeriodContainingDate(
            List<SchedulePeriod> periods, Date date, LocalDate scheduleStart, LocalDate scheduleEnd) {
        return periods.stream()
                .filter(p -> isPeriodContainingDate(p, date))
                .peek(p -> LOGGER.debug("Date {} found in period {} - {}", date, p.getStartDate(), p.getEndDate()))
                .findFirst()
                .map(p -> buildCalculationPeriodData(p, scheduleStart, scheduleEnd))
                .orElse(null);
    }

    private boolean isPeriodContainingDate(SchedulePeriod period, Date date) {
        LocalDate localDate = date.toLocalDate();
        return !period.getStartDate().isAfter(localDate) && !period.getEndDate().isBefore(localDate);
    }

    private CalculationPeriodDataBuilder buildCalculationPeriodData(
            SchedulePeriod targetPeriod, LocalDate scheduleStart, LocalDate scheduleEnd) {
        LOGGER.debug("Building CalculationPeriodData for period {} - {}",
                targetPeriod.getStartDate(), targetPeriod.getEndDate());
        return CalculationPeriodData.builder()
                .setStartDate(Date.of(targetPeriod.getStartDate()))
                .setEndDate(Date.of(targetPeriod.getEndDate()))
                .setDaysInLeapYearPeriod(getDaysThatAreInLeapYear(targetPeriod))
                .setDaysInPeriod(
                        (int) ChronoUnit.DAYS.between(targetPeriod.getStartDate(), targetPeriod.getEndDate()) + 1)
                .setIsFirstPeriod(targetPeriod.getStartDate().equals(scheduleStart))
                .setIsLastPeriod(targetPeriod.getEndDate().equals(scheduleEnd));
    }

    private int getDaysThatAreInLeapYear(SchedulePeriod targetPeriod) {
        int daysThatAreInLeapYear = 0;
        for (LocalDate d = targetPeriod.getStartDate(); !d.isAfter(targetPeriod.getEndDate()); d = d.plusDays(1)) {
            if (IsoChronology.INSTANCE.isLeapYear(d.getYear())) {
                daysThatAreInLeapYear++;
            }
        }
        return daysThatAreInLeapYear;
    }

    private static <T> T checkNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return value;
    }
}

