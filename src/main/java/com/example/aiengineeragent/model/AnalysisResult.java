package com.example.aiengineeragent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisResult {

    private String summary;
    private List<String> risks = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private String severity;
    private List<Evidence> evidence = new ArrayList<>();
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

    public List<Evidence> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<Evidence> evidence) {
        this.evidence = evidence;
    }

    public String getRawLLMResponse() {
        return rawLLMResponse;
    }

    public void setRawLLMResponse(String rawLLMResponse) {
        this.rawLLMResponse = rawLLMResponse;
    }
}
