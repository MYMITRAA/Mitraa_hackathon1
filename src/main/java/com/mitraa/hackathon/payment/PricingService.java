package com.mitraa.hackathon.payment;

import com.mitraa.hackathon.registration.ParticipationType;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class PricingService {
    private static final Set<String> INDIA_NAMES = Set.of("india", "in", "ind", "bharat");

    public EntryPrice priceFor(String country, ParticipationType type) {
        boolean india = country != null
                && INDIA_NAMES.contains(country.trim().toLowerCase(Locale.ROOT));

        if (india) {
            return type == ParticipationType.INDIVIDUAL
                    ? new EntryPrice(1401, "INR", "₹14.01")
                    : new EntryPrice(1993, "INR", "₹19.93");
        }

        return type == ParticipationType.INDIVIDUAL
                ? new EntryPrice(270, "USD", "$2.70")
                : new EntryPrice(580, "USD", "$5.80");
    }
}
