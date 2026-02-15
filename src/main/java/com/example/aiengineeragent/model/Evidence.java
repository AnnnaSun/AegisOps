package com.example.aiengineeragent.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Evidence {

    // Canonical API keys for Day3+ contract.
    @JsonAlias("type")
    private String source;
    @JsonAlias("detail")
    private String snippet;

    public Evidence() {
    }

    public Evidence(String source, String snippet) {
        this.source = source;
        this.snippet = snippet;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    // Backward-compatible accessors for older code/tests using type/detail naming.
    @JsonIgnore
    public String getType() {
        return source;
    }

    @JsonIgnore
    public void setType(String type) {
        this.source = type;
    }

    @JsonIgnore
    public String getDetail() {
        return snippet;
    }

    @JsonIgnore
    public void setDetail(String detail) {
        this.snippet = detail;
    }
}
