package com.example.aiengineeragent.controller;

import com.example.aiengineeragent.model.AnalysisResult;
import com.example.aiengineeragent.model.AnalyzeLogRequest;
import com.example.aiengineeragent.service.AnalyzeService;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/analyze")
public class AnalyzeController {

    private final AnalyzeService analyzeService;

    public AnalyzeController(AnalyzeService analyzeService) {
        this.analyzeService = analyzeService;
    }

    @PostMapping("/log")
    public Mono<AnalysisResult> analyzeLog(@Valid @RequestBody AnalyzeLogRequest request) {
        return analyzeService.analyzeLog(request.getLog());
    }
}
