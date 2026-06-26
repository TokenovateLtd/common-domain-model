package cdm.product.common.schedule.functions;

import cdm.product.common.schedule.CalculationPeriodDates;
import cdm.product.common.schedule.StubPeriodTypeEnum;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.RollConvention;
import com.opengamma.strata.basics.schedule.StubConvention;
import org.checkerframework.checker.units.qual.C;

import java.util.List;

class CdmToStrataMapper {

    static Frequency getFrequency(CalculationPeriodDates calculationPeriodDates) {
        return Frequency.parse(calculationPeriodDates.getCalculationPeriodFrequency().getPeriodMultiplier().toString() + calculationPeriodDates.getCalculationPeriodFrequency().getPeriod().toString());
    }

    static RollConvention getRollConvention(CalculationPeriodDates calculationPeriodDates) {
            String rollConventionName = calculationPeriodDates.getCalculationPeriodFrequency().getRollConvention().toString();
            // The display name of the match RollConvention using FpML
            return RollConvention.extendedEnum().externalNames("FpML").lookup(rollConventionName);
    }

    static StubConvention getStubConvention(List<StubPeriodTypeEnum> stubTypes) {

        StubConvention stubConvention = null;
        if (stubTypes != null && stubTypes.size() >= 2) {
            stubConvention = StubConvention.BOTH;
        } else if (stubTypes != null && stubTypes.size() == 1) {
            switch (stubTypes.get(0)) {
                case SHORT_INITIAL: stubConvention = StubConvention.SHORT_INITIAL; break;
                case LONG_INITIAL:  stubConvention = StubConvention.LONG_INITIAL;  break;
                case SHORT_FINAL:   stubConvention = StubConvention.SHORT_FINAL;   break;
                case LONG_FINAL:    stubConvention = StubConvention.LONG_FINAL;    break;
                default: throw new IllegalArgumentException("Unknown stub period type: " + stubTypes.get(0));
            }
        }
        return stubConvention;
    }

}
