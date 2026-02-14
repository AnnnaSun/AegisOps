package com.example.aiengineeragent.rule;

import com.example.aiengineeragent.model.AnalysisResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Rule priority convention:
 * - HIGH deterministic rules: 10-99
 * - Medium-confidence rules: 100-199
 * - Fallback-style rules: 900+
 */
@Component
@Order(10)
public class OOMRule implements LogRule {

    @Override
    public String name() {
        return "OOMRule";
    }

    @Override
    public Optional<AnalysisResult> apply(String logText) {
        String normalized = logText == null ? "" : logText.toLowerCase(Locale.ROOT);
        if (!containsAny(normalized,
                "outofmemoryerror",
                "java heap space",
                "gc overhead limit exceeded")) {
            return Optional.empty();
        }

        AnalysisResult result = new AnalysisResult();
        result.setSummary("Detected JVM memory exhaustion symptom from logs.");
        result.setSeverity("HIGH");
        result.setRisks(List.of(
                "Service may keep restarting or become unavailable.",
                "In-flight requests can fail and increase error rates."
        ));
        result.setSuggestions(List.of(
                "Check heap settings (Xms/Xmx) and recent traffic or payload growth.",
                "Capture heap dump and identify top memory consumers before rollout."
        ));
        return Optional.of(result);
    }

    private boolean containsAny(String text, String... patterns) {
        for (String pattern : patterns) {
            if (text.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
