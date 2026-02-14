package com.example.aiengineeragent.analyzer;

import com.example.aiengineeragent.llm.LLMClient;
import com.example.aiengineeragent.model.AnalysisResult;
import com.example.aiengineeragent.rule.LogRule;
import com.example.aiengineeragent.util.SeverityHeuristics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class LogAnalyzer implements Analyzer<String, AnalysisResult> {

    private static final Logger log = LoggerFactory.getLogger(LogAnalyzer.class);

    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;
    private final List<LogRule> logRules;

    public LogAnalyzer(LLMClient llmClient, ObjectMapper objectMapper, List<LogRule> logRules) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.logRules = logRules;
    }

    @Override
    public Mono<AnalysisResult> analyze(String input) {
        log.debug("Start log analysis. logLength={}", input == null ? 0 : input.length());

        // Rule-first: deterministic matches should bypass LLM for speed and stability.
        for (LogRule logRule : logRules) {
            Optional<AnalysisResult> matched = logRule.apply(input);
            if (matched.isPresent()) {
                log.info("Rule matched. rule={}, callLlm=false", logRule.name());
                AnalysisResult result = normalize(matched.get(), input);
                log.debug("Finish log analysis by rule. severity={}", result.getSeverity());
                return Mono.just(result);
            }
        }

        log.info("No rule matched. callLlm=true");
        String prompt = buildPrompt(input);
        return llmClient.generate(prompt)
                .map(llmOutput -> {
                    // Parse model output into typed DTO; fallback on invalid JSON.
                    AnalysisResult result = parseOrFallback(llmOutput, input);
                    log.debug("Finish log analysis. severity={}", result.getSeverity());
                    return result;
                })
                .onErrorResume(ex -> {
                    log.error("LLM call failed. Returning fallback result. reason={}", ex.getMessage());
                    return Mono.just(buildFallbackForLlmFailure(input, ex));
                });
    }

    private String buildPrompt(String logText) {
        return "You are a senior Java/Spring production troubleshooting assistant.\n"
                + "Analyze the following log and output JSON only.\n"
                + "Do NOT output markdown, explanation, or code fences.\n"
                + "Output must be a valid JSON object with fields:\n"
                + "{\"summary\":\"string\",\"risks\":[\"string\"],\"suggestions\":[\"string\"],\"severity\":\"LOW|MEDIUM|HIGH\",\"evidence\":[{\"source\":\"TOOL|KB|CASE|LOG\",\"snippet\":\"string\"}]}\n"
                + "If uncertain, still return valid JSON with conservative values and empty arrays.\n"
                + "Log content:\n"
                + logText;
    }

    private AnalysisResult parseOrFallback(String llmOutput, String input) {
        if (llmOutput == null || llmOutput.isBlank()) {
            log.warn("LLM output is blank. useFallback=true");
            return buildParseFallback(input, llmOutput);
        }

        try {
            AnalysisResult parsed = objectMapper.readValue(llmOutput, AnalysisResult.class);
            AnalysisResult normalized = normalize(parsed, input);
            normalized.setRawLLMResponse(llmOutput);
            log.info("LLM JSON parsed successfully. parseSuccess=true");
            return normalized;
        } catch (JsonProcessingException ex) {
            // Keep controller response stable even if model ignores JSON contract.
            log.warn("LLM JSON parse failed. parseSuccess=false, reason={}", ex.getOriginalMessage());
            return buildParseFallback(input, llmOutput);
        }
    }

    private AnalysisResult buildParseFallback(String input, String llmOutput) {
        AnalysisResult fallback = new AnalysisResult();
        fallback.setSummary("Model output is not valid JSON. Returned fallback analysis.");
        fallback.setSeverity(SeverityHeuristics.judge(input));
        fallback.setRisks(List.of("Structured extraction failed for current model output."));
        fallback.setSuggestions(List.of("Review rawLLMResponse and tune prompt/model for strict JSON output."));
        fallback.setRawLLMResponse(llmOutput);
        return normalize(fallback, input);
    }

    private AnalysisResult buildFallbackForLlmFailure(String input, Throwable ex) {
        AnalysisResult fallback = new AnalysisResult();
        fallback.setSummary("LLM request failed. Returned fallback analysis.");
        fallback.setSeverity(SeverityHeuristics.judge(input));
        fallback.setRisks(List.of("Unable to get model analysis due to LLM invocation failure."));
        fallback.setSuggestions(List.of("Check Ollama status/network and retry with the same log."));
        fallback.setRawLLMResponse(ex.getMessage());
        return normalize(fallback, input);
    }

    private AnalysisResult normalize(AnalysisResult result, String input) {
        AnalysisResult normalized = result == null ? new AnalysisResult() : result;

        // Normalize null collections to avoid null-check burden in API consumers.
        if (normalized.getSummary() == null) {
            normalized.setSummary("");
        }
        if (normalized.getRisks() == null) {
            normalized.setRisks(new ArrayList<>());
        }
        if (normalized.getSuggestions() == null) {
            normalized.setSuggestions(new ArrayList<>());
        }
        if (normalized.getEvidence() == null) {
            normalized.setEvidence(new ArrayList<>());
        }
        if (normalized.getSeverity() == null || normalized.getSeverity().isBlank()) {
            normalized.setSeverity(SeverityHeuristics.judge(input));
        }
        if (normalized.getSeverity() != null) {
            normalized.setSeverity(normalized.getSeverity().trim().toUpperCase());
        }

        if (!"LOW".equals(normalized.getSeverity())
                && !"MEDIUM".equals(normalized.getSeverity())
                && !"HIGH".equals(normalized.getSeverity())) {
            normalized.setSeverity(SeverityHeuristics.judge(input));
        }

        return normalized;
    }
}
