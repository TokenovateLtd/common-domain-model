package cdm.product.common.schedule.functions;

import cdm.base.datetime.RollConventionEnum;
import com.rosetta.model.lib.records.Date;
import org.finos.cdm.functions.AbstractFunctionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.DateTimeException;

import static org.junit.jupiter.api.Assertions.*;

class AdjustDateToRollConventionTest extends AbstractFunctionTest {
  @Inject
  AdjustDateToRollConvention adjustDateToRollConvention;

  @Test
  @DisplayName("should adjust day of the month to numeric roll convention of 15")
  void shouldAdjustNumericRollConvention() {
    Date input = Date.of(2025, 4, 1);

    Date result = adjustDateToRollConvention.evaluate(input, RollConventionEnum._15);

    assertNotNull(result, "Adjusted date should not be null");
    assertEquals(Date.of(2025, 4, 15), result, "Expected day of month to be set to 15");
  }

  @Test
  @DisplayName("should adjust to end of month for EOM roll convention")
  void shouldAdjustToEOM() {
    Date input = Date.of(2025, 4, 1);

    Date result = adjustDateToRollConvention.evaluate(input, RollConventionEnum.EOM);

    assertNotNull(result, "Adjusted date should not be null");
    assertEquals(Date.of(2025, 4, 30), result, "Expected last day of the month for EOM");
  }

  @Test
  @DisplayName("should adjust to third Wednesday for IMM roll convention")
  void shouldAdjustToIMM() {
    Date input = Date.of(2025, 4, 1);

    Date result = adjustDateToRollConvention.evaluate(input, RollConventionEnum.IMM);

    assertNotNull(result, "Adjusted date should not be null");
    assertEquals(Date.of(2025, 4, 16), result, "Expected third Wednesday of April 2025 for IMM");
  }

  @Test
  @DisplayName("should adjust to second London business day prior to third Wednesday for IMMCAD")
  void shouldAdjustToIMMCAD() {
    Date input = Date.of(2025, 4, 1);

    Date result = adjustDateToRollConvention.evaluate(input, RollConventionEnum.IMMCAD);

    assertNotNull(result, "Adjusted date should not be null");
    assertEquals(Date.of(2025, 4, 14), result, "Expected April 14, 2025 for IMMCAD convention");
  }

  @Test
  @DisplayName("should adjust to one Sydney business day preceding second Friday for IMMAUD")
  void shouldAdjustToIMMAUD() {
    Date input = Date.of(2025, 4, 1);

    Date result = adjustDateToRollConvention.evaluate(input, RollConventionEnum.IMMAUD);

    assertNotNull(result, "Adjusted date should not be null");
    assertEquals(Date.of(2025, 4, 10), result, "Expected April 10, 2025 for IMMAUD convention");
  }

  @Test
  @DisplayName("should adjust to first Wednesday after ninth day for IMMNZD")
  void shouldAdjustToIMMNZD() {
    Date input = Date.of(2025, 4, 1);

    Date result = adjustDateToRollConvention.evaluate(input, RollConventionEnum.IMMNZD);

    assertNotNull(result, "Adjusted date should not be null");
    assertEquals(Date.of(2025, 4, 16), result, "Expected April 16, 2025 for IMMNZD convention");
  }

  @Test
  @DisplayName("should adjust to second Friday for SFE roll convention")
  void shouldAdjustToSFE() {
    Date input = Date.of(2025, 4, 1);

    Date result = adjustDateToRollConvention.evaluate(input, RollConventionEnum.SFE);

    assertNotNull(result, "Adjusted date should not be null");
    assertEquals(Date.of(2025, 4, 11), result, "Expected second Friday (April 11, 2025) for SFE");
  }

  @Test
  @DisplayName("should adjust to Monday for TBILL roll convention")
  void shouldAdjustToTBILL() {
    Date input = Date.of(2025, 4, 1);

    Date result = adjustDateToRollConvention.evaluate(input, RollConventionEnum.TBILL);

    assertNotNull(result, "Adjusted date should not be null");
    assertEquals(
        Date.of(2025, 4, 7), result, "Expected April 7, 2025 (Monday) for TBILL convention");
  }

  @Test
  @DisplayName("should adjust to next or same weekday for THU roll convention")
  void shouldAdjustToWeekday() {
    Date input = Date.of(2025, 4, 1);

    Date result = adjustDateToRollConvention.evaluate(input, RollConventionEnum.THU);

    assertNotNull(result, "Adjusted date should not be null");
    assertEquals(
        Date.of(2025, 4, 3), result, "Expected April 3, 2025 (Thursday) for THU convention");
  }

  @Test
  @DisplayName("should throw when date to adjust is null")
  void shouldThrowWhenDateIsNull() {
    IllegalArgumentException ex =
        assertThrows(
                IllegalArgumentException.class,
            () -> adjustDateToRollConvention.evaluate(null, RollConventionEnum.EOM),
            "Expected IllegalArgumentException when dateToAdjust is null");
    assertEquals("Missing input date to adjust!", ex.getMessage());
  }

  @Test
  @DisplayName("should throw when roll convention is null")
  void shouldThrowWhenRollConventionIsNull() {
    IllegalArgumentException ex =
        assertThrows(
                IllegalArgumentException.class,
            () -> adjustDateToRollConvention.evaluate(Date.of(2025, 4, 1), null),
            "Expected IllegalArgumentException when rollConvention is null");
    assertEquals("Missing roll Convention", ex.getMessage());
  }

  @Test
  @DisplayName("should throw DateTimeException for invalid numeric roll (30 in February)")
  void shouldThrowForInvalidNumericRoll() {
    Date input = Date.of(2025, 2, 1);

    assertThrows(
        DateTimeException.class,
        () -> adjustDateToRollConvention.evaluate(input, RollConventionEnum._30),
        "Expected DateTimeException when applying roll 31 to February");
  }
}
