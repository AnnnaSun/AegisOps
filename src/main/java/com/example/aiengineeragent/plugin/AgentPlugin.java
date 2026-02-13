package com.example.aiengineeragent.plugin;

import com.example.aiengineeragent.model.AnalysisResult;

public interface AgentPlugin {
    String name();

    AnalysisResult execute(String input);
}
