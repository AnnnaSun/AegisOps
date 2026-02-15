package com.example.aiengineeragent.tool;

/**
 * Minimal tool contract for analyzer pre-processing steps.
 */
public interface Tool {

    String name();

    ToolResult execute(String logText);
}
