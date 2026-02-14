package com.example.aiengineeragent.analyzer;

import com.example.aiengineeragent.llm.LLMClient;
import com.example.aiengineeragent.model.AnalysisResult;
import com.example.aiengineeragent.rule.LogRule;
import com.example.aiengineeragent.rule.OOMRule;
import com.example.aiengineeragent.util.SeverityHeuristics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogAnalyzerTest {

    @Test
    void oomRuleShouldShortCircuitWithoutCallingLlm() {
        RecordingLLMClient llmClient = new RecordingLLMClient(prompt -> Mono.just("{}"));
        LogAnalyzer analyzer = new LogAnalyzer(llmClient, new ObjectMapper(), List.of(new OOMRule()));

        AnalysisResult result = analyzer.analyze("java.lang.OutOfMemoryError: Java heap space").block();

        assertNotNull(result);
        assertEquals(0, llmClient.callCount());
        assertEquals("HIGH", result.getSeverity());
        assertFalse(result.getSummary().isBlank());
    }

    @Test
    void shouldParseLlmJsonSuccessfully() {
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
        LogAnalyzer analyzer = new LogAnalyzer(llmClient, new ObjectMapper(), List.<LogRule>of());

        AnalysisResult result = analyzer.analyze("Connection reset by peer").block();

        assertNotNull(result);
        assertEquals(1, llmClient.callCount());
        assertEquals("DB connection issue detected.", result.getSummary());
        assertEquals(1, result.getRisks().size());
        assertEquals(1, result.getSuggestions().size());
        assertEquals("MEDIUM", result.getSeverity());
        assertEquals(llmJson, result.getRawLLMResponse());
        assertEquals(1, result.getEvidence().size());
        assertEquals("LOG", result.getEvidence().getFirst().getType());
        assertEquals("Connection reset by peer", result.getEvidence().getFirst().getDetail());
    }

    @Test
    void shouldFallbackWhenLlmReturnsNonJson() {
        String nonJson = "not json";
        String input = "some error";
        RecordingLLMClient llmClient = new RecordingLLMClient(prompt -> Mono.just(nonJson));
        LogAnalyzer analyzer = new LogAnalyzer(llmClient, new ObjectMapper(), List.<LogRule>of());

        AnalysisResult result = analyzer.analyze(input).block();

        assertNotNull(result);
        assertEquals("Model output is not valid JSON. Returned fallback analysis.", result.getSummary());
        assertEquals(SeverityHeuristics.judge(input), result.getSeverity());
        assertEquals(nonJson, result.getRawLLMResponse());
        assertTrue(result.getEvidence().isEmpty());
    }

    @Test
    void shouldFallbackWhenLlmCallFails() {
        String input = "database timeout";
        RecordingLLMClient llmClient = new RecordingLLMClient(prompt -> Mono.error(new RuntimeException("boom")));
        LogAnalyzer analyzer = new LogAnalyzer(llmClient, new ObjectMapper(), List.<LogRule>of());

        AnalysisResult result = analyzer.analyze(input).block();

        assertNotNull(result);
        assertEquals("LLM request failed. Returned fallback analysis.", result.getSummary());
        assertEquals(SeverityHeuristics.judge(input), result.getSeverity());
        assertNotNull(result.getRawLLMResponse());
        assertTrue(result.getRawLLMResponse().contains("boom"));
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
