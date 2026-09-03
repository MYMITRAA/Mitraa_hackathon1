package com.mitraa.hackathon.common;

import java.util.Locale;

public final class InputNormalizer {

    private InputNormalizer() {
    }

    public static String capitalizeFirstCharacter(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim();
        if (cleaned.isEmpty()) {
            return cleaned;
        }

        return cleaned.substring(0, 1).toUpperCase(Locale.ROOT)
                + cleaned.substring(1);
    }

    public static void requireNoOuterPasswordWhitespace(String password) {
        if (password == null || !password.equals(password.strip())) {
            throw new IllegalArgumentException(
                    "Password must not start or end with a space."
            );
        }
    }

    public static String normalizePhone(String phone, String country) {
        String original = phone == null ? "" : phone.trim();
        String digits = original.replaceAll("\\D", "");
        boolean india = country != null
                && (country.trim().equalsIgnoreCase("India")
                || country.trim().equalsIgnoreCase("IN")
                || country.trim().equalsIgnoreCase("IND")
                || country.trim().equalsIgnoreCase("Bharat"));

        if (india) {
            if (digits.startsWith("91") && digits.length() > 10) {
                digits = digits.substring(2);
            }
            if (digits.length() < 6 || digits.length() > 10) {
                throw new IllegalArgumentException(
                        "Indian mobile number must contain at most 10 digits, excluding +91."
                );
            }
            return "+91" + digits;
        }

        if (digits.length() < 6 || digits.length() > 15) {
            throw new IllegalArgumentException(
                    "Phone number must contain 6–15 digits including the country code."
            );
        }

        return original.startsWith("+") ? "+" + digits : digits;
    }
}
