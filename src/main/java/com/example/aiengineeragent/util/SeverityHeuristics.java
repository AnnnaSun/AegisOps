package com.example.aiengineeragent.util;

import java.util.Locale;

public final class SeverityHeuristics {

    private SeverityHeuristics() {
    }

    /**
     * Simple Day1 severity rules based on keyword matching.
     */
    public static String judge(String text) {
        if (text == null || text.isBlank()) {
            return "LOW";
        }

        String normalized = text.toLowerCase(Locale.ROOT);

        if (containsAny(normalized,
                "outofmemoryerror",
                "gc overhead",
                "stackoverflowerror",
                "connection refused")) {
            return "HIGH";
        }

        if (containsAny(normalized,
                "timeout",
                "slow query",
                "deadlock")) {
            return "MEDIUM";
        }

        return "LOW";
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
