package com.example.aiengineeragent.model;

import java.util.ArrayList;
import java.util.List;

public class AnalysisResult {

    private String summary;
    private List<String> risks = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private String severity;
    private String rawLLMResponse;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getRisks() {
        return risks;
    }

    public void setRisks(List<String> risks) {
        this.risks = risks;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getRawLLMResponse() {
        return rawLLMResponse;
    }

    public void setRawLLMResponse(String rawLLMResponse) {
        this.rawLLMResponse = rawLLMResponse;
    }
}
