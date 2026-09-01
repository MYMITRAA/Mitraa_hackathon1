package com.mitraa.hackathon.payment;

import com.mitraa.hackathon.registration.ParticipationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {
    private final PricingService pricing = new PricingService();

    @Test void indiaUsesInr() {
        assertThat(pricing.priceFor("India", ParticipationType.INDIVIDUAL))
                .isEqualTo(new EntryPrice(1401, "INR", "₹14.01"));
        assertThat(pricing.priceFor(" india ", ParticipationType.TEAM))
                .isEqualTo(new EntryPrice(1993, "INR", "₹19.93"));
    }

    @Test void otherCountriesUseUsd() {
        assertThat(pricing.priceFor("Norway", ParticipationType.INDIVIDUAL))
                .isEqualTo(new EntryPrice(270, "USD", "$2.70"));
        assertThat(pricing.priceFor("United States", ParticipationType.TEAM))
                .isEqualTo(new EntryPrice(580, "USD", "$5.80"));
    }
}
