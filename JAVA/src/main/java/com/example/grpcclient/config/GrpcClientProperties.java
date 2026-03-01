package com.example.grpcclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grpc.client")
public record GrpcClientProperties(
        String serviceUrl,
        String outputPath,
        boolean writeToDisk,
        boolean showEnhancedProgressBar,
        int channelCapacity,
        int maxDegreeOfParallelism,
        int progressUpdateThreshold
) {
    public GrpcClientProperties {
        // Set defaults if not provided
        if (outputPath == null || outputPath.trim().isEmpty()) {
            outputPath = System.getProperty("user.dir") + "/output";
        }
        if (channelCapacity <= 0) {
            channelCapacity = 1000;
        }
        if (maxDegreeOfParallelism <= 0) {
            maxDegreeOfParallelism = Runtime.getRuntime().availableProcessors();
        }
        if (progressUpdateThreshold <= 0) {
            progressUpdateThreshold = 10;
        }
    }
}