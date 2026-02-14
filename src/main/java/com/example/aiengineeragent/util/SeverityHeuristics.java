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
                "java heap space",
                "gc overhead limit exceeded",
                "gc overhead",
                "stackoverflowerror",
                "connection refused",
                "no route to host",
                "unable to acquire jdbc connection",
                "cannot allocate memory",
                "too many open files",
                "disk full",
                "broken pipe",
                "segmentation fault",
                "fatal",
                "panic",
                "corrupt")) {
            return "HIGH";
        }

        if (containsAny(normalized,
                "timeout",
                "timed out",
                "sockettimeoutexception",
                "slow query",
                "deadlock",
                "lock wait timeout",
                "connection reset",
                "connection reset by peer",
                "retry exhausted",
                "circuitbreaker open",
                "rate limit",
                "too many requests",
                "service unavailable",
                "thread starvation",
                "pool exhausted",
                "falling back",
                "fallback to")) {
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
