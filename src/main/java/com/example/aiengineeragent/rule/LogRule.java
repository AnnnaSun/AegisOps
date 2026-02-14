package com.example.aiengineeragent.rule;

import com.example.aiengineeragent.model.AnalysisResult;

import java.util.Optional;

public interface LogRule {

    String name();

    Optional<AnalysisResult> apply(String logText);
}
