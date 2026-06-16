package cdm.product.common.schedule.functions;

import cdm.base.datetime.RollConventionEnum;
import com.rosetta.model.lib.records.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Adjusts a date to match a given roll convention.
 *
 * <p>Supports numeric DOM and other special conventions
 * (EOM/IMM/IMMCAD/IMMAUD/IMMNZD/SFE/TBILL/WEEKDAYS). Throws {@link IllegalArgumentException} for
 * invalid inputs or requests.
 */
class AdjustDateToRollConvention {
  private static final Logger logger = LoggerFactory.getLogger(AdjustDateToRollConvention.class);

  public Date evaluate(Date dateToAdjust, RollConventionEnum rollConvention) {
    logger.debug("Evaluating roll adjustment: date={}, roll={}", dateToAdjust, rollConvention);
    validateInput(dateToAdjust, rollConvention);
    Date adjustedDate = doEvaluate(dateToAdjust, rollConvention);

    logger.debug("AdjustDateToRollConvention result: {}", adjustedDate);
    return adjustedDate;
  }

  protected Date doEvaluate(Date dateToAdjust, RollConventionEnum rollConvention) {
    LocalDate localDateToAdjust = dateToAdjust.toLocalDate();
    if (isNumericRollConvention(rollConvention)) {
      int dayOfMonth =
          Integer.parseInt(rollConvention.toString()); // Remove underscore and convert to integer
      localDateToAdjust = localDateToAdjust.withDayOfMonth(dayOfMonth);
    } else {
      localDateToAdjust =
          adjustEndDateForNonNumericRollConvention(localDateToAdjust, rollConvention);
    }

    return Date.of(localDateToAdjust);
  }

  private LocalDate adjustEndDateForNonNumericRollConvention(
      LocalDate endDate, RollConventionEnum rollConvention) {
    if (rollConvention == RollConventionEnum.EOM) {
      return endDate.withDayOfMonth(endDate.lengthOfMonth());
    } else if (rollConvention == RollConventionEnum.IMM) {
      // Third Wednesday of the month
      return endDate.with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.WEDNESDAY));
    } else if (rollConvention == RollConventionEnum.IMMCAD) {
      // Second London banking day prior to the third Wednesday of the month
      // Assume London banking days are Monday to Friday for this example
      LocalDate thirdWednesday =
          endDate.with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.WEDNESDAY));
      return adjustToPreviousBankingDay(thirdWednesday.minusDays(2));
    } else if (rollConvention == RollConventionEnum.IMMAUD) {
      // One Sydney business day preceding the second Friday
      // Assume Sydney business days are Monday to Friday for this example
      LocalDate secondFriday =
          endDate.with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.FRIDAY));
      return adjustToPreviousBankingDay(secondFriday.minusDays(1));
    } else if (rollConvention == RollConventionEnum.IMMNZD) {
      // First Wednesday after the ninth day of the month
      return endDate.withDayOfMonth(9).with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
    } else if (rollConvention == RollConventionEnum.SFE) {
      // Second Friday of the month
      return endDate.with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.FRIDAY));
    } else if (rollConvention == RollConventionEnum.TBILL) {
      // Each Monday or Tuesday if Monday is a U.S. holiday
      LocalDate monday = endDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
      return isUSHoliday() ? monday.plusDays(1) : monday;
    } else if (rollConvention == RollConventionEnum.MON) {
      return endDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    } else if (rollConvention == RollConventionEnum.TUE) {
      return endDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
    } else if (rollConvention == RollConventionEnum.WED) {
      return endDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY));
    } else if (rollConvention == RollConventionEnum.THU) {
      return endDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY));
    } else if (rollConvention == RollConventionEnum.FRI) {
      return endDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
    } else if (rollConvention == RollConventionEnum.SAT) {
      return endDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
    } else if (rollConvention == RollConventionEnum.SUN) {
      return endDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    } else {
      return endDate;
    }
  }

  private LocalDate adjustToPreviousBankingDay(LocalDate date) {
    logger.info("Assuming banking days are Monday to Friday");
    if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
      return date.minusDays(1);
    } else if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
      return date.minusDays(2);
    }
    return date;
  }

  private boolean isUSHoliday() {
    // Placeholder for US holiday check
    // You need to implement your own logic to check if a given date is a holiday
    // This can be done by checking against a list of known holidays
    // For example:
    // if (date.equals(LocalDate.of(date.getYear(), Month.JANUARY, 1))) return true; // New Year's
    // Day
    // Add other holidays...
    logger.warn("US holiday calendar is not provided or handled! Returning false.");
    return false;
  }

  private boolean isNumericRollConvention(RollConventionEnum rollConvention) {
    return rollConvention.toString().matches("\\d+");
  }

  private void validateInput(Date dateToAdjust, RollConventionEnum rollConvention) {
    if (dateToAdjust == null) {
      throw new IllegalArgumentException("Missing input date to adjust!");
    }
    if (rollConvention == null) {
      throw new IllegalArgumentException("Missing roll Convention");
    }
  }
}
