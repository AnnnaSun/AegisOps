package com.example.aiengineeragent.tool.impl;

import com.example.aiengineeragent.tool.Tool;
import com.example.aiengineeragent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LogGrepTool implements Tool {

    // Keep output compact so prompt remains focused and token cost stays bounded.
    private static final int MAX_LINES = 8;

    @Override
    public String name() {
        return "logGrep";
    }

    @Override
    public ToolResult execute(String logText) {
        if (logText == null || logText.isBlank()) {
            return new ToolResult(name(), "logGrep: input log is empty.");
        }

        // Day3 minimal extraction: line scan with simple keyword matching.
        String[] lines = logText.split("\\n");
        List<String> matches = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (containsSignal(line)) {
                matches.add(line.trim());
            }
            if (matches.size() >= MAX_LINES) {
                break;
            }
        }

        if (matches.isEmpty()) {
            return new ToolResult(name(), "logGrep: no Exception/Error lines matched.");
        }
        return new ToolResult(name(), String.join("\n", matches));
    }

    private boolean containsSignal(String line) {
        return line.contains("Exception")
                || line.contains("Error")
                || line.contains("FATAL")
                || line.contains("Caused by:");
    }
}
