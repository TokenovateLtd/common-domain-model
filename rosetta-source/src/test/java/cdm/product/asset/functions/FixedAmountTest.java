package cdm.product.asset.functions;

import cdm.base.datetime.*;
import cdm.base.datetime.daycount.DayCountFractionEnum;
import cdm.base.datetime.daycount.metafields.FieldWithMetaDayCountFractionEnum;
import cdm.base.datetime.metafields.ReferenceWithMetaBusinessCenters;
import cdm.base.math.UnitType;
import cdm.observable.asset.Money;
import cdm.observable.asset.PriceSchedule;
import cdm.product.asset.FixedRateSpecification;
import cdm.product.asset.InterestRatePayout;
import cdm.product.asset.RateSpecification;
import cdm.product.common.schedule.CalculationPeriodDates;
import cdm.product.common.schedule.RateSchedule;
import com.rosetta.model.lib.records.Date;
import org.finos.cdm.functions.AbstractFunctionTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FixedAmountTest extends AbstractFunctionTest {

    @Inject
    private FixedAmount fixedAmount;

    @Test
    void shouldCalculate() {
        BigDecimal price = BigDecimal.valueOf(0.06);

        Money notional = Money.builder()
                .setValue(BigDecimal.valueOf(50_000_000))
                .setUnit(UnitType.builder().setCurrencyValue("USD"))
                .build();

        InterestRatePayout interestRatePayout = InterestRatePayout.builder()
                .setDayCountFraction(FieldWithMetaDayCountFractionEnum.builder().setValue(DayCountFractionEnum._30E_360).build())
                .setCalculationPeriodDates(CalculationPeriodDates.builder()
                        .setEffectiveDate((AdjustableOrRelativeDate.builder()
                                .setAdjustableDate(AdjustableDate.builder()
                                        .setUnadjustedDate(Date.of(2018, 1, 3))
                                        .setDateAdjustments(BusinessDayAdjustments.builder()
                                                .setBusinessDayConvention(BusinessDayConventionEnum.NONE)
                                                .build())
                                        .build())
                                .build()))
                        .setTerminationDate(AdjustableOrRelativeDate.builder()
                                .setAdjustableDate(AdjustableDate.builder()
                                        .setUnadjustedDate(Date.of(2020, 1, 3))
                                        .setDateAdjustments(BusinessDayAdjustments.builder()
                                                .setBusinessDayConvention(BusinessDayConventionEnum.MODFOLLOWING)
                                                .setBusinessCenters(BusinessCenters.builder()
                                                        .setBusinessCentersReference(ReferenceWithMetaBusinessCenters.builder()
                                                                .setExternalReference("primaryBusinessCenters")
                                                                .build())
                                                        .build())
                                                .build())
                                        .build())
                                .build())
                        .setCalculationPeriodFrequency(CalculationPeriodFrequency.builder()
                                .setRollConvention(RollConventionEnum._3)
                                .setPeriodMultiplier(3)
                                .setPeriod(PeriodExtendedEnum.M)
                                .build())
                        .setCalculationPeriodDatesAdjustments(BusinessDayAdjustments.builder()
                                .setBusinessDayConvention(BusinessDayConventionEnum.MODFOLLOWING)
                                .setBusinessCenters(BusinessCenters.builder()
                                        .setBusinessCentersReference(ReferenceWithMetaBusinessCenters.builder()
                                                .setExternalReference("primaryBusinessCenters")
                                                .build())
                                        .build())
                                .build())
                        .build())
                .setRateSpecification(RateSpecification.builder().setFixedRateSpecification(FixedRateSpecification.builder()
                        .setRateSchedule(RateSchedule.builder().setPriceValue(PriceSchedule.builder().setValue(price)))))
                .build();

        // With TKN's ISDA non-overlapping period adjustment, the second and subsequent periods start
        // the day after the previous period ends. For this schedule (roll on 3rd), Aug 22 falls in
        // the period [Jul 4, Oct 3] rather than the Strata-native [Jul 3, Oct 3].
        // 30E/360 on [Jul 4, Oct 3] = (3*30 + (3-4)) / 360 = 89/360
        // amount = 50,000,000 * 0.06 * 89/360 ≈ 741,666.67
        BigDecimal result = fixedAmount.evaluate(interestRatePayout, notional.getValue(), Date.of(2018, 8, 22), null);
        assertEquals(50_000_000 * 0.06 * 89.0 / 360.0, result.doubleValue(), 0.01);
    }
}
