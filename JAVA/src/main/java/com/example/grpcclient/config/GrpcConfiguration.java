package com.example.grpcclient.config;

import com.example.grpcclient.config.GrpcClientProperties;
import com.example.grpcclient.proto.MakePDFGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(GrpcClientProperties.class)
public class GrpcConfiguration {

    private final GrpcClientProperties properties;

    public GrpcConfiguration(GrpcClientProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ManagedChannel grpcChannel() throws SSLException {
        if (properties.serviceUrl() == null || properties.serviceUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Service URL is not configured. Please set grpc.client.service-url in application.yml");
        }

        URI serviceUri = URI.create(properties.serviceUrl());
        boolean isHttps = "https".equals(serviceUri.getScheme());
        int port = serviceUri.getPort() != -1 ? serviceUri.getPort() : (isHttps ? 443 : 80);

        NettyChannelBuilder builder = NettyChannelBuilder
                .forAddress(serviceUri.getHost(), port)
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .maxInboundMessageSize(32 * 1024 * 1024) // 32MB max message size
                .usePlaintext();

        if (isHttps) {
            // Configure SSL context to ignore certificate validation (similar to .NET implementation)
            builder = builder
                    .sslContext(GrpcSslContexts.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build())
                    .useTransportSecurity();
        }

        ManagedChannel channel = builder.build();
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!channel.isShutdown()) {
                try {
                    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    channel.shutdownNow();
                }
            }
        }));
        
        return channel;
    }

    @Bean
    public MakePDFGrpc.MakePDFBlockingStub makePDFBlockingStub(ManagedChannel channel) {
        return MakePDFGrpc.newBlockingStub(channel);
    }

    @Bean
    public MakePDFGrpc.MakePDFStub makePDFAsyncStub(ManagedChannel channel) {
        return MakePDFGrpc.newStub(channel);
    }
}