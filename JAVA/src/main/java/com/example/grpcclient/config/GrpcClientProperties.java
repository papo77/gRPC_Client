package com.example.grpcclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grpc.client")
public class GrpcClientProperties {
    
    private String serviceUrl;
    private String outputPath;
    private boolean writeToDisk;
    private boolean showEnhancedProgressBar;
    private int channelCapacity;
    private int maxDegreeOfParallelism;
    private int progressUpdateThreshold;

    public GrpcClientProperties() {
        // Default constructor for Spring
        this.channelCapacity = 1000;
        this.maxDegreeOfParallelism = Runtime.getRuntime().availableProcessors();
        this.progressUpdateThreshold = 10;
    }

    // Getters
    public String serviceUrl() { return serviceUrl; }
    public String outputPath() { 
        return (outputPath == null || outputPath.trim().isEmpty()) 
                ? System.getProperty("user.dir") + "/output" 
                : outputPath;
    }
    public boolean writeToDisk() { return writeToDisk; }
    public boolean showEnhancedProgressBar() { return showEnhancedProgressBar; }
    public int channelCapacity() { return channelCapacity <= 0 ? 1000 : channelCapacity; }
    public int maxDegreeOfParallelism() { 
        return maxDegreeOfParallelism <= 0 ? Runtime.getRuntime().availableProcessors() : maxDegreeOfParallelism; 
    }
    public int progressUpdateThreshold() { return progressUpdateThreshold <= 0 ? 10 : progressUpdateThreshold; }

    // Setters for Spring property binding
    public void setServiceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; }
    public void setOutputPath(String outputPath) { this.outputPath = outputPath; }
    public void setWriteToDisk(boolean writeToDisk) { this.writeToDisk = writeToDisk; }
    public void setShowEnhancedProgressBar(boolean showEnhancedProgressBar) { this.showEnhancedProgressBar = showEnhancedProgressBar; }
    public void setChannelCapacity(int channelCapacity) { this.channelCapacity = channelCapacity; }
    public void setMaxDegreeOfParallelism(int maxDegreeOfParallelism) { this.maxDegreeOfParallelism = maxDegreeOfParallelism; }
    public void setProgressUpdateThreshold(int progressUpdateThreshold) { this.progressUpdateThreshold = progressUpdateThreshold; }
}