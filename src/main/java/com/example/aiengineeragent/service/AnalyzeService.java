package com.example.aiengineeragent.service;

import com.example.aiengineeragent.analyzer.LogAnalyzer;
import com.example.aiengineeragent.model.AnalysisResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AnalyzeService {

    private final LogAnalyzer logAnalyzer;

    public AnalyzeService(LogAnalyzer logAnalyzer) {
        this.logAnalyzer = logAnalyzer;
    }

    public Mono<AnalysisResult> analyzeLog(String log) {
        return logAnalyzer.analyze(log);
    }
}
