package com.example.aiengineeragent.analyzer;

import com.example.aiengineeragent.llm.LLMClient;
import com.example.aiengineeragent.model.AnalysisResult;
import com.example.aiengineeragent.util.SeverityHeuristics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Component
public class LogAnalyzer implements Analyzer<String, AnalysisResult> {

    private static final Logger log = LoggerFactory.getLogger(LogAnalyzer.class);

    private final LLMClient llmClient;

    public LogAnalyzer(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public Mono<AnalysisResult> analyze(String input) {
        log.debug("Start log analysis. logLength={}", input == null ? 0 : input.length());

        String prompt = buildPrompt(input);
        return llmClient.generate(prompt)
                .map(llmOutput -> {
                    AnalysisResult result = parse(llmOutput);
                    result.setRawLLMResponse(llmOutput);
                    result.setSeverity(SeverityHeuristics.judge(input + "\n" + llmOutput));
                    log.debug("Finish log analysis. severity={}", result.getSeverity());
                    return result;
                });
    }

    private String buildPrompt(String logText) {
        return "You are a senior Java/Spring production troubleshooting assistant.\n"
                + "Analyze the following log and return in this exact format:\n"
                + "SUMMARY:\n"
                + "<one concise paragraph>\n\n"
                + "RISKS:\n"
                + "- <risk 1>\n"
                + "- <risk 2>\n\n"
                + "SUGGESTIONS:\n"
                + "- <actionable suggestion 1>\n"
                + "- <actionable suggestion 2>\n\n"
                + "Log content:\n"
                + logText;
    }

    /**
     * Day1 parser: split model output by fixed section headers.
     * If headers are missing, keep raw response and return empty structured fields.
     */
    private AnalysisResult parse(String llmOutput) {
        AnalysisResult result = new AnalysisResult();
        if (llmOutput == null || llmOutput.isBlank()) {
            return result;
        }

        String summary = extractSection(llmOutput, "SUMMARY:", "RISKS:");
        String risksBlock = extractSection(llmOutput, "RISKS:", "SUGGESTIONS:");
        String suggestionsBlock = extractSection(llmOutput, "SUGGESTIONS:", null);

        if (summary == null && risksBlock == null && suggestionsBlock == null) {
            return result;
        }

        result.setSummary(summary == null ? "" : summary.trim());
        result.setRisks(parseBulletLines(risksBlock));
        result.setSuggestions(parseBulletLines(suggestionsBlock));
        return result;
    }

    /**
     * Extracts text between section markers (or to the end when endMarker is null).
     */
    private String extractSection(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        if (start < 0) {
            return null;
        }
        start += startMarker.length();
        int end = endMarker == null ? -1 : text.indexOf(endMarker, start);
        if (end < 0) {
            return text.substring(start).trim();
        }
        return text.substring(start, end).trim();
    }

    private List<String> parseBulletLines(String block) {
        List<String> lines = new ArrayList<>();
        if (block == null || block.isBlank()) {
            return lines;
        }

        String[] rawLines = block.split("\\R");
        for (String raw : rawLines) {
            String item = raw.trim();
            if (item.isEmpty()) {
                continue;
            }
            if (item.startsWith("-")) {
                item = item.substring(1).trim();
            }
            lines.add(item);
        }
        return lines;
    }
}
