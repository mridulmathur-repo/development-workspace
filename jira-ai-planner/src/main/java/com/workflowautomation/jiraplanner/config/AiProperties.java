package com.workflowautomation.jiraplanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai")
public class AiProperties {

    private String baseUrl = "https://api.groq.com/openai/v1";
    private String apiKey = "";
    private String model = "openai/gpt-oss-120b";
    private int maxOutputTokens = 5000;
    private double temperature = 0.1;
    private int codeMaxContextChars = 120000;
    private int codeMaxFiles = 40;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getCodeMaxContextChars() { return codeMaxContextChars; }
    public void setCodeMaxContextChars(int value) { this.codeMaxContextChars = value; }
    public int getCodeMaxFiles() { return codeMaxFiles; }
    public void setCodeMaxFiles(int value) { this.codeMaxFiles = value; }
}
