package com.example.aiengineeragent.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Evidence {

    // Keep compatibility with LLM outputs using source/snippet keys.
    // Internally we still expose type/detail to avoid breaking existing API usage.
    @JsonAlias("source")
    private String type;
    @JsonAlias("snippet")
    private String detail;

    public Evidence() {
    }

    public Evidence(String type, String detail) {
        this.type = type;
        this.detail = detail;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
