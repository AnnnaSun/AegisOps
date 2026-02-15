package com.example.aiengineeragent.analyzer;

import com.example.aiengineeragent.llm.LLMClient;
import com.example.aiengineeragent.model.AnalysisResult;
import com.example.aiengineeragent.model.Evidence;
import com.example.aiengineeragent.rule.LogRule;
import com.example.aiengineeragent.tool.Tool;
import com.example.aiengineeragent.tool.ToolResult;
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
import java.util.UUID;

@Component
public class LogAnalyzer implements Analyzer<String, AnalysisResult> {

    private static final Logger log = LoggerFactory.getLogger(LogAnalyzer.class);

    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;
    private final List<LogRule> logRules;
    private final List<Tool> tools;

    public LogAnalyzer(LLMClient llmClient, ObjectMapper objectMapper, List<LogRule> logRules, List<Tool> tools) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.logRules = logRules;
        this.tools = tools;
    }

    @Override
    public Mono<AnalysisResult> analyze(String input) {
        final String caseId = UUID.randomUUID().toString();
        final long totalStartNanos = System.nanoTime();
        log.debug("Start log analysis. logLength={}", input == null ? 0 : input.length());

        // Day2.1 behavior: deterministic rules always short-circuit before tool/LLM.
        for (LogRule logRule : logRules) {
            Optional<AnalysisResult> matched = logRule.apply(input);
            if (matched.isPresent()) {
                log.info("Rule matched. rule={}, callLlm=false", logRule.name());
                AnalysisResult result = normalize(matched.get(), input);
                log.info("ANALYZE_DONE caseId={} severitySource=RULE finalSeverity={} parseSuccess=false llmCallFail=false",
                        caseId, result.getSeverity());
                log.debug("Finish log analysis by rule. severity={}", result.getSeverity());
                return Mono.just(result);
            }
        }

        // Day3 behavior: non-rule path runs tool first, then feeds condensed signal to LLM.
        ToolResult toolResult = runTool(input);
        log.info("No rule matched. callTool=true, tool={}, callLlm=true", toolResult.getToolName());
        String prompt = buildPrompt(input, toolResult.getOutput());
        final int promptLen = safeLen(prompt);
        final int toolLen = safeLen(toolResult.getOutput());
        // Deterministic severity fix:
        // Same input could previously return LOW/MEDIUM due to LLM variance.
        // We now always compute final severity from deterministic heuristics.
        final String severityBasisText = resolveSeverityBasisText(input, toolResult);
        final String heuristicSeverity = SeverityHeuristics.judge(severityBasisText);
        final String modelName = llmClient.modelName();
        log.info("LLM_CALL caseId={} model={} promptLen={} toolLen={}",
                caseId, modelName, promptLen, toolLen);
        final long llmStartNanos = System.nanoTime();
        return llmClient.generate(prompt)
                .map(llmOutput -> {
                    ParseOutcome outcome = parseOrFallback(llmOutput, input, toolResult);
                    // Force final response severity to heuristic result on non-rule paths,
                    // while keeping llmSeverityRaw only for observability.
                    AnalysisResult result = applyDeterministicSeverity(outcome.result(), heuristicSeverity);
                    long llmLatencyMs = elapsedMs(llmStartNanos);
                    long totalLatencyMs = elapsedMs(totalStartNanos);
                    log.info("LLM_DONE caseId={} model={} severitySource=HEURISTIC heuristicSeverity={} llmSeverityRaw={} parseSuccess={} llmCallFail=false toolLen={} promptLen={} llmLatencyMs={} totalLatencyMs={}",
                            caseId,
                            modelName,
                            heuristicSeverity,
                            sanitizeForSingleLineLog(outcome.llmSeverityRaw()),
                            outcome.parseSuccess(),
                            toolLen,
                            promptLen,
                            llmLatencyMs,
                            totalLatencyMs);
                    log.debug("Finish log analysis. severity={}", result.getSeverity());
                    return result;
                })
                .onErrorResume(ex -> {
                    // Keep fallback path consistent with parse-success path:
                    // final severity still comes from the same deterministic heuristic.
                    AnalysisResult result = applyDeterministicSeverity(buildFallbackForLlmFailure(input, ex, toolResult), heuristicSeverity);
                    long llmLatencyMs = elapsedMs(llmStartNanos);
                    long totalLatencyMs = elapsedMs(totalStartNanos);
                    log.info("LLM_FAIL caseId={} model={} severitySource=HEURISTIC heuristicSeverity={} llmSeverityRaw={} parseSuccess=false llmCallFail=true toolLen={} promptLen={} llmLatencyMs={} totalLatencyMs={} reason={}",
                            caseId,
                            modelName,
                            heuristicSeverity,
                            "",
                            toolLen,
                            promptLen,
                            llmLatencyMs,
                            totalLatencyMs,
                            sanitizeForSingleLineLog(ex.getMessage()));
                    log.error("LLM call failed. Returning fallback result. reason={}", ex.getMessage());
                    return Mono.just(result);
                });
    }

    private String buildPrompt(String logText, String toolOutput) {
        String truncatedLog = truncate(logText, 2000);
        return "You are a senior Java/Spring production troubleshooting assistant.\n"
                + "Analyze the provided signal and output JSON only.\n"
                + "Do NOT output markdown, explanation, or code fences.\n"
                + "Output must be a valid JSON object with fields:\n"
                + "{\"summary\":\"string\",\"risks\":[\"string\"],\"suggestions\":[\"string\"],\"severity\":\"LOW|MEDIUM|HIGH\",\"evidence\":[{\"source\":\"TOOL|KB|CASE|LOG\",\"snippet\":\"string\"}]}\n"
                + "If uncertain, still return valid JSON with conservative values and empty arrays.\n"
                + "Tool extraction result (primary signal):\n"
                + toolOutput + "\n"
                + "Raw log excerpt (truncated):\n"
                + truncatedLog;
    }

    private ParseOutcome parseOrFallback(String llmOutput, String input, ToolResult toolResult) {
        if (llmOutput == null || llmOutput.isBlank()) {
            log.warn("LLM output is blank. useFallback=true");
            return new ParseOutcome(buildParseFallback(input, llmOutput, toolResult), false, "");
        }

        try {
            AnalysisResult parsed = objectMapper.readValue(llmOutput, AnalysisResult.class);
            String llmSeverityRaw = parsed.getSeverity();
            AnalysisResult normalized = normalize(parsed, input);
            ensureToolEvidence(normalized, toolResult);
            normalized.setRawLLMResponse(llmOutput);
            log.info("LLM JSON parsed successfully. parseSuccess=true");
            return new ParseOutcome(normalized, true, llmSeverityRaw);
        } catch (JsonProcessingException ex) {
            // Keep controller response stable even if model ignores JSON contract.
            log.warn("LLM JSON parse failed. parseSuccess=false, reason={}", ex.getOriginalMessage());
            return new ParseOutcome(buildParseFallback(input, llmOutput, toolResult), false, "");
        }
    }

    private AnalysisResult buildParseFallback(String input, String llmOutput, ToolResult toolResult) {
        AnalysisResult fallback = new AnalysisResult();
        fallback.setSummary("Model output is not valid JSON. Returned fallback analysis.");
        fallback.setSeverity(SeverityHeuristics.judge(input));
        fallback.setRisks(List.of("Structured extraction failed for current model output."));
        fallback.setSuggestions(List.of("Review rawLLMResponse and tune prompt/model for strict JSON output."));
        fallback.setRawLLMResponse(llmOutput);
        AnalysisResult normalized = normalize(fallback, input);
        ensureToolEvidence(normalized, toolResult);
        return normalized;
    }

    private AnalysisResult buildFallbackForLlmFailure(String input, Throwable ex, ToolResult toolResult) {
        AnalysisResult fallback = new AnalysisResult();
        fallback.setSummary("LLM request failed. Returned fallback analysis.");
        fallback.setSeverity(SeverityHeuristics.judge(input));
        fallback.setRisks(List.of("Unable to get model analysis due to LLM invocation failure."));
        fallback.setSuggestions(List.of("Check Ollama status/network and retry with the same log."));
        fallback.setRawLLMResponse(ex.getMessage());
        AnalysisResult normalized = normalize(fallback, input);
        ensureToolEvidence(normalized, toolResult);
        return normalized;
    }

    private ToolResult runTool(String input) {
        // Prefer logGrep explicitly; fallback to first available tool to keep analyzer resilient.
        Tool selected = tools.stream()
                .filter(tool -> "logGrep".equalsIgnoreCase(tool.name()))
                .findFirst()
                .orElseGet(() -> tools.isEmpty() ? null : tools.getFirst());
        if (selected == null) {
            return new ToolResult("logGrep", "logGrep: tool unavailable.");
        }
        ToolResult result = selected.execute(input);
        if (result == null || result.getOutput() == null || result.getOutput().isBlank()) {
            return new ToolResult(selected.name(), selected.name() + ": no output.");
        }
        return result;
    }

    private void ensureToolEvidence(AnalysisResult result, ToolResult toolResult) {
        if (result == null || toolResult == null) {
            return;
        }
        // Fallback and parse-success paths both must preserve TOOL evidence.
        boolean hasToolEvidence = result.getEvidence().stream()
                .anyMatch(evidence -> evidence != null && "TOOL".equalsIgnoreCase(evidence.getSource()));
        if (!hasToolEvidence) {
            result.getEvidence().addFirst(new Evidence("TOOL", toolResult.getOutput()));
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n...(truncated)";
    }

    private int safeLen(String value) {
        return value == null ? 0 : value.length();
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String sanitizeForSingleLineLog(String value) {
        return truncate(value, 200)
                .replace('\n', ' ')
                .replace('\r', ' ');
    }

    private AnalysisResult applyDeterministicSeverity(AnalysisResult result, String heuristicSeverity) {
        AnalysisResult normalized = normalize(result, null);
        normalized.setSeverity(heuristicSeverity);
        return normalized;
    }

    private String resolveSeverityBasisText(String input, ToolResult toolResult) {
        // Prefer tool output because extracted error lines are shorter and less noisy than full logs,
        // which improves heuristic stability for identical incidents.
        if (toolResult != null && toolResult.getOutput() != null && !toolResult.getOutput().isBlank()) {
            return toolResult.getOutput();
        }
        return input;
    }

    private record ParseOutcome(AnalysisResult result, boolean parseSuccess, String llmSeverityRaw) {
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
