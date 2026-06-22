package cdm.event.instructioncomposition.functions;

import cdm.event.instructioncomposition.CompositionStepInstructions;
import cdm.event.instructioncomposition.reset.DetermineUnadjustedCalculationPeriodInstruction;
import cdm.event.instructioncomposition.reset.ResetInstructionState;
import cdm.product.common.schedule.CalculationPeriodBase;
import com.rosetta.model.lib.records.Date;
import org.finos.cdm.functions.AbstractFunctionTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UpdateResetCompositionStateTest extends AbstractFunctionTest {

    @Inject
    private UpdateResetCompositionState updateResetCompositionState;

    @Nested
    @DisplayName("Step 2 - Unadjusted Calculation Period & Reset Date")
    class DetermineUnadjustedCalculationPeriodAndResetDateTests {

        @Test
        @DisplayName("Takes unadjusted calculation period and reset date from the next step when present")
        void shouldTakeUnadjustedCalculationPeriodAndResetDateFromNextStepWhenPresent() {
            CalculationPeriodBase period = CalculationPeriodBase.builder()
                    .setAdjustedStartDate(Date.of(2023, 1, 3))
                    .setAdjustedEndDate(Date.of(2023, 4, 3))
                    .build();
            Date resetDate = Date.of(2023, 2, 15);

            CompositionStepInstructions nextStep = CompositionStepInstructions.builder()
                    .setDetermineUnadjustedCalculationPeriod(
                            DetermineUnadjustedCalculationPeriodInstruction.builder()
                                    .setUnadjustedCalculationPeriod(period)
                                    .setUnadjustedResetDate(resetDate))
                    .build();

            ResetInstructionState result = updateResetCompositionState.evaluate(null, nextStep);

            assertEquals(period, result.getUnadjustedCalculationPeriod(), "Unadjusted calculation period should be set");
            assertEquals(resetDate, result.getUnadjustedResetDate(), "Unadjusted reset date should be set");
        }

        @Test
        @DisplayName("Preserves unadjusted calculation period and reset date from current state when absent from next step")
        void shouldPreserveUnadjustedCalculationPeriodAndResetDateFromCurrentStateWhenAbsentFromNextStep() {
            CalculationPeriodBase existingPeriod = CalculationPeriodBase.builder()
                    .setAdjustedStartDate(Date.of(2022, 7, 1))
                    .setAdjustedEndDate(Date.of(2022, 10, 1))
                    .build();
            Date existingResetDate = Date.of(2022, 8, 10);

            ResetInstructionState currentState = ResetInstructionState.builder()
                    .setUnadjustedCalculationPeriod(existingPeriod)
                    .setUnadjustedResetDate(existingResetDate)
                    .build();

            CompositionStepInstructions nextStepWithoutStep2 = CompositionStepInstructions.builder().build();

            ResetInstructionState result = updateResetCompositionState.evaluate(currentState, nextStepWithoutStep2);

            assertEquals(existingPeriod, result.getUnadjustedCalculationPeriod());
            assertEquals(existingResetDate, result.getUnadjustedResetDate());
        }

        @Test
        @DisplayName("Returns null fields when absent from both next step and current state")
        void shouldReturnNullUnadjustedCalculationPeriodAndResetDateWhenAbsentFromBothNextStepAndCurrentState() {
            ResetInstructionState result = updateResetCompositionState.evaluate(null, CompositionStepInstructions.builder().build());

            assertNull(result.getUnadjustedCalculationPeriod());
            assertNull(result.getUnadjustedResetDate());
        }
    }
}
