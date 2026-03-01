package com.example.grpcclient;

import com.example.grpcclient.service.GrpcClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.example.grpcclient.config")
public class GrpcClientApplication implements CommandLineRunner {

    private final GrpcClientService grpcClientService;

    @Autowired
    public GrpcClientApplication(GrpcClientService grpcClientService) {
        this.grpcClientService = grpcClientService;
    }

    public static void main(String[] args) {
        // Check if benchmark argument is provided
        if (args.length > 0 && "benchmark".equalsIgnoreCase(args[0])) {
            System.out.println("Running benchmarks...");
            // JMH benchmark runner would go here
            runBenchmarks();
            return;
        }

        SpringApplication.run(GrpcClientApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Starting bi-directional streaming gRPC client...");

        // Test both unary and bi-directional streaming
        //grpcClientService.callUnaryMethod();
        grpcClientService.callBidirectionalStreaming();

        System.out.println("Press Enter to exit...");
        System.in.read();
    }

    private static void runBenchmarks() {
        try {
            org.openjdk.jmh.Main.main(new String[]{});
        } catch (Exception e) {
            System.err.println("Error running benchmarks: " + e.getMessage());
            e.printStackTrace();
        }
    }
}