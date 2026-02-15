package com.example.aiengineeragent.tool;

/**
 * Standardized output from tools so analyzer can reuse it in prompt and evidence.
 */
public class ToolResult {

    private final String toolName;
    private final String output;

    public ToolResult(String toolName, String output) {
        this.toolName = toolName;
        this.output = output;
    }

    public String getToolName() {
        return toolName;
    }

    public String getOutput() {
        return output;
    }
}
