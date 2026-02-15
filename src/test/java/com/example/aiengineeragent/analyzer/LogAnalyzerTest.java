package com.example.aiengineeragent.analyzer;

import com.example.aiengineeragent.llm.LLMClient;
import com.example.aiengineeragent.model.AnalysisResult;
import com.example.aiengineeragent.rule.LogRule;
import com.example.aiengineeragent.rule.OOMRule;
import com.example.aiengineeragent.tool.Tool;
import com.example.aiengineeragent.tool.impl.LogGrepTool;
import com.example.aiengineeragent.util.SeverityHeuristics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogAnalyzerTest {

    private static final LogGrepTool LOG_GREP_TOOL = new LogGrepTool();

    @Test
    void oomRuleShouldShortCircuitWithoutCallingLlm() {
        RecordingLLMClient llmClient = new RecordingLLMClient(prompt -> Mono.just("{}"));
        LogAnalyzer analyzer = new LogAnalyzer(
                llmClient, new ObjectMapper(), List.of(new OOMRule()), List.of(LOG_GREP_TOOL));

        AnalysisResult result = analyzer.analyze("java.lang.OutOfMemoryError: Java heap space").block();

        assertNotNull(result);
        assertEquals(0, llmClient.callCount());
        assertEquals("HIGH", result.getSeverity());
        assertFalse(result.getSummary().isBlank());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void shouldParseLlmJsonSuccessfully(CapturedOutput output) {
        String llmJson = """
                {
                  "summary":"DB connection issue detected.",
                  "risks":["Service latency increases."],
                  "suggestions":["Check DB pool and upstream network."],
                  "severity":"medium",
                  "evidence":[{"source":"LOG","snippet":"Connection reset by peer"}]
                }
                """;
        RecordingLLMClient llmClient = new RecordingLLMClient(prompt -> Mono.just(llmJson));
        LogAnalyzer analyzer = new LogAnalyzer(
                llmClient, new ObjectMapper(), List.<LogRule>of(), List.<Tool>of(LOG_GREP_TOOL));

        String input = "Connection reset by peer\njava.lang.RuntimeException: boom";
        AnalysisResult result = analyzer.analyze(input).block();

        assertNotNull(result);
        assertEquals(1, llmClient.callCount());
        assertEquals("DB connection issue detected.", result.getSummary());
        assertEquals(1, result.getRisks().size());
        assertEquals(1, result.getSuggestions().size());
        assertEquals(expectedHeuristicSeverity(input), result.getSeverity());
        assertEquals(llmJson, result.getRawLLMResponse());
        assertEquals(2, result.getEvidence().size());
        assertEquals("TOOL", result.getEvidence().getFirst().getSource());
        assertTrue(result.getEvidence().getFirst().getSnippet().contains("RuntimeException"));
        assertEquals("LOG", result.getEvidence().get(1).getSource());
        assertEquals("Connection reset by peer", result.getEvidence().get(1).getSnippet());
        assertTrue(output.getOut().contains("LLM_DONE caseId="));
        assertTrue(output.getOut().contains("parseSuccess=true llmCallFail=false"));
        assertTrue(output.getOut().contains("severitySource=HEURISTIC"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void shouldFallbackWhenLlmReturnsNonJson(CapturedOutput output) {
        String nonJson = "not json";
        String input = "some error";
        RecordingLLMClient llmClient = new RecordingLLMClient(prompt -> Mono.just(nonJson));
        LogAnalyzer analyzer = new LogAnalyzer(
                llmClient, new ObjectMapper(), List.<LogRule>of(), List.<Tool>of(LOG_GREP_TOOL));

        AnalysisResult result = analyzer.analyze(input).block();

        assertNotNull(result);
        assertEquals("Model output is not valid JSON. Returned fallback analysis.", result.getSummary());
        assertEquals(expectedHeuristicSeverity(input), result.getSeverity());
        assertEquals(nonJson, result.getRawLLMResponse());
        assertFalse(result.getEvidence().isEmpty());
        assertEquals("TOOL", result.getEvidence().getFirst().getSource());
        assertTrue(output.getOut().contains("LLM_DONE caseId="));
        assertTrue(output.getOut().contains("parseSuccess=false llmCallFail=false"));
        assertTrue(output.getOut().contains("severitySource=HEURISTIC"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void shouldFallbackWhenLlmCallFails(CapturedOutput output) {
        String input = "database timeout";
        RecordingLLMClient llmClient = new RecordingLLMClient(prompt -> Mono.error(new RuntimeException("boom")));
        LogAnalyzer analyzer = new LogAnalyzer(
                llmClient, new ObjectMapper(), List.<LogRule>of(), List.<Tool>of(LOG_GREP_TOOL));

        AnalysisResult result = analyzer.analyze(input).block();

        assertNotNull(result);
        assertEquals("LLM request failed. Returned fallback analysis.", result.getSummary());
        assertEquals(expectedHeuristicSeverity(input), result.getSeverity());
        assertNotNull(result.getRawLLMResponse());
        assertTrue(result.getRawLLMResponse().contains("boom"));
        assertFalse(result.getEvidence().isEmpty());
        assertEquals("TOOL", result.getEvidence().getFirst().getSource());
        assertTrue(output.getOut().contains("LLM_FAIL caseId="));
        assertTrue(output.getOut().contains("parseSuccess=false llmCallFail=true"));
        assertTrue(output.getOut().contains("severitySource=HEURISTIC"));
    }

    @Test
    void shouldAlwaysUseHeuristicSeverityWhenNoRuleMatches() {
        String input = "Connection reset by peer\njava.lang.RuntimeException: boom";
        String expectedSeverity = expectedHeuristicSeverity(input);
        for (String llmSeverity : List.of("LOW", "MEDIUM", "HIGH")) {
            String llmJson = """
                    {
                      "summary":"signal",
                      "risks":[],
                      "suggestions":[],
                      "severity":"%s",
                      "evidence":[]
                    }
                    """.formatted(llmSeverity);
            RecordingLLMClient llmClient = new RecordingLLMClient(prompt -> Mono.just(llmJson));
            LogAnalyzer analyzer = new LogAnalyzer(
                    llmClient, new ObjectMapper(), List.<LogRule>of(), List.<Tool>of(LOG_GREP_TOOL));

            AnalysisResult result = analyzer.analyze(input).block();
            assertNotNull(result);
            assertEquals(expectedSeverity, result.getSeverity());
        }
    }

    private String expectedHeuristicSeverity(String input) {
        String basis = LOG_GREP_TOOL.execute(input).getOutput();
        return SeverityHeuristics.judge(basis == null || basis.isBlank() ? input : basis);
    }

    private static final class RecordingLLMClient implements LLMClient {
        private final AtomicInteger callCount = new AtomicInteger();
        private final Function<String, Mono<String>> behavior;

        private RecordingLLMClient(Function<String, Mono<String>> behavior) {
            this.behavior = behavior;
        }

        @Override
        public Mono<String> generate(String prompt) {
            callCount.incrementAndGet();
            return behavior.apply(prompt);
        }

        private int callCount() {
            return callCount.get();
        }
    }
}
