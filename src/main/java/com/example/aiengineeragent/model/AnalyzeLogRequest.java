package com.example.aiengineeragent.model;

import jakarta.validation.constraints.NotBlank;

public class AnalyzeLogRequest {

    @NotBlank(message = "log must not be blank")
    private String log;

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }
}
