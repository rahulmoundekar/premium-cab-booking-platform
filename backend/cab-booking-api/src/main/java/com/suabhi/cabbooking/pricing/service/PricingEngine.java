package com.suabhi.cabbooking.pricing.service;

import com.suabhi.cabbooking.journey.provider.ServiceType;
import com.suabhi.cabbooking.pricing.domain.FareCalculationInput;
import com.suabhi.cabbooking.pricing.domain.FareCalculationResult;
import com.suabhi.cabbooking.pricing.domain.PricingCalculationType;
import com.suabhi.cabbooking.pricing.domain.PricingComponent;
import com.suabhi.cabbooking.pricing.domain.PricingComponentType;
import com.suabhi.cabbooking.pricing.domain.PricingRateCard;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PricingEngine {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000);
    private static final BigDecimal SECONDS_PER_DAY = BigDecimal.valueOf(86_400);
    private static final int MONEY_SCALE = 2;

    public FareCalculationResult calculate(PricingRateCard rateCard, FareCalculationInput input) {
        if (rateCard == null || input == null) {
            throw new IllegalArgumentException("A rate card and calculation input are required.");
        }
        if (rateCard.serviceType() != input.serviceType()
                || rateCard.operatingCity() != input.operatingCity()) {
            throw new IllegalArgumentException("The selected rate card does not match the journey service and city.");
        }
        if (!rateCard.active()) {
            throw new IllegalArgumentException("The selected rate card is not active.");
        }

        BigDecimal distanceKm = BigDecimal.valueOf(input.distanceMeters())
                .divide(THOUSAND, 3, RoundingMode.HALF_UP);
        BigDecimal billableKm = distanceKm.subtract(rateCard.includedKilometers()).max(ZERO);
        BigDecimal distanceCharge = money(billableKm.multiply(rateCard.perKilometerRate()));
        BigDecimal usageCharge = ZERO;
        BigDecimal perDayCharge = ZERO;
        BigDecimal driverAllowance = ZERO;

        if (input.serviceType() == ServiceType.LOCAL_RENTAL) {
            BigDecimal billableHours = BigDecimal.valueOf(input.rentalHours())
                    .subtract(rateCard.includedHours()).max(ZERO);
            usageCharge = money(billableHours.multiply(rateCard.perHourRate()));
        } else if (input.serviceType() == ServiceType.OUTSTATION) {
            long days = Math.max(1L, divideCeiling(input.durationSeconds(), SECONDS_PER_DAY));
            perDayCharge = money(rateCard.perDayRate().multiply(BigDecimal.valueOf(days)));
            driverAllowance = money(rateCard.driverAllowancePerDay().multiply(BigDecimal.valueOf(days)));
        }

        BigDecimal rawCore = money(rateCard.baseFare()
                .add(distanceCharge)
                .add(usageCharge)
                .add(perDayCharge)
                .add(driverAllowance));
        BigDecimal coreSubtotal = rawCore.max(money(rateCard.minimumFare()));
        BigDecimal minimumAdjustment = money(coreSubtotal.subtract(rawCore).max(ZERO));

        List<FareCalculationResult.LineItem> lineItems = new ArrayList<>();
        BigDecimal surchargeTotal = ZERO;
        for (PricingComponent component : rateCard.components()) {
            if (component.componentType() != PricingComponentType.SURCHARGE) {
                continue;
            }
            BigDecimal amount = componentAmount(component, coreSubtotal);
            surchargeTotal = surchargeTotal.add(amount);
            lineItems.add(new FareCalculationResult.LineItem(
                    component.code(), component.displayName(), component.componentType(), amount, component.taxable()));
        }
        surchargeTotal = money(surchargeTotal);

        BigDecimal taxableSurcharges = rateCard.components().stream()
                .filter(component -> component.componentType() == PricingComponentType.SURCHARGE && component.taxable())
                .map(component -> componentAmount(component, coreSubtotal))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal taxBasis = money(coreSubtotal.add(taxableSurcharges));
        BigDecimal taxTotal = ZERO;
        for (PricingComponent component : rateCard.components()) {
            if (component.componentType() != PricingComponentType.TAX) {
                continue;
            }
            BigDecimal amount = componentAmount(component, taxBasis);
            taxTotal = taxTotal.add(amount);
            lineItems.add(new FareCalculationResult.LineItem(
                    component.code(), component.displayName(), component.componentType(), amount, false));
        }
        taxTotal = money(taxTotal);

        return new FareCalculationResult(
                rateCard.currency(),
                distanceKm,
                billableKm.setScale(3, RoundingMode.HALF_UP),
                money(rateCard.baseFare()),
                distanceCharge,
                money(usageCharge),
                money(perDayCharge),
                money(driverAllowance),
                minimumAdjustment,
                surchargeTotal,
                taxTotal,
                money(coreSubtotal.add(surchargeTotal).add(taxTotal)),
                lineItems
        );
    }

    private static BigDecimal componentAmount(PricingComponent component, BigDecimal basis) {
        BigDecimal amount = component.calculationType() == PricingCalculationType.FIXED
                ? component.amount()
                : basis.multiply(component.amount()).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        return money(amount);
    }

    private static long divideCeiling(long value, BigDecimal divisor) {
        return BigDecimal.valueOf(value).divide(divisor, 0, RoundingMode.CEILING).longValueExact();
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
