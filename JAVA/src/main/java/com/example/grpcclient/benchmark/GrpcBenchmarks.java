package com.example.grpcclient.benchmark;

import com.example.grpcclient.config.GrpcClientProperties;
import com.example.grpcclient.proto.GeneratePDFReply;
import com.example.grpcclient.proto.GeneratePDFRequest;
import com.example.grpcclient.proto.MakePDFGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(1)
public class GrpcBenchmarks {

    @State(Scope.Benchmark)
    public static class GrpcState {
        ManagedChannel channel;
        MakePDFGrpc.MakePDFBlockingStub blockingStub;
        MakePDFGrpc.MakePDFStub asyncStub;

        @Setup(Level.Trial)
        public void setup() {
            // Configure for benchmark (use properties or defaults)
            String serviceUrl = System.getProperty("grpc.benchmark.url", "https://localhost:5555");
            
            channel = ManagedChannelBuilder
                    .forTarget(serviceUrl)
                    .usePlaintext() // For benchmarking, use plaintext for simplicity
                    .build();
            
            blockingStub = MakePDFGrpc.newBlockingStub(channel);
            asyncStub = MakePDFGrpc.newStub(channel);
        }

        @TearDown(Level.Trial)
        public void teardown() throws InterruptedException {
            if (channel != null) {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }

    @Benchmark
    @Group("unary")
    public void benchmarkUnaryCall(GrpcState state, Blackhole blackhole) {
        var request = GeneratePDFRequest.newBuilder()
                .setFirstName("Benchmark")
                .setLastName("User")
                .build();

        try {
            GeneratePDFReply response = state.blockingStub.generatePDF(request);
            blackhole.consume(response);
        } catch (Exception e) {
            // Consume the exception to prevent JVM optimizations
            blackhole.consume(e);
        }
    }

    @Benchmark
    @Group("streaming")
    @OperationsPerInvocation(100) // Process 100 requests per invocation
    public void benchmarkStreamingCall(GrpcState state, Blackhole blackhole) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        int requestCount = 100;
        
        var responseObserver = new StreamObserver<GeneratePDFReply>() {
            private int received = 0;
            
            @Override
            public void onNext(GeneratePDFReply reply) {
                blackhole.consume(reply);
                if (++received == requestCount) {
                    latch.countDown();
                }
            }

            @Override
            public void onError(Throwable t) {
                blackhole.consume(t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        };

        var requestObserver = state.asyncStub.streamPDFs(responseObserver);

        try {
            for (int i = 0; i < requestCount; i++) {
                var request = GeneratePDFRequest.newBuilder()
                        .setFirstName("Benchmark" + i)
                        .setLastName("User" + i)
                        .build();
                requestObserver.onNext(request);
            }
            requestObserver.onCompleted();
            
            // Wait for all responses
            latch.await(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            blackhole.consume(e);
        }
    }

    @Benchmark
    @Group("mixed")
    public void benchmarkMixedCalls(GrpcState state, Blackhole blackhole) throws InterruptedException {
        // 70% unary, 30% streaming
        if (Math.random() < 0.7) {
            benchmarkUnaryCall(state, blackhole);
        } else {
            // Smaller streaming batch for mixed workload
            CountDownLatch latch = new CountDownLatch(1);
            int requestCount = 10;
            
            var responseObserver = new StreamObserver<GeneratePDFReply>() {
                private int received = 0;
                
                @Override
                public void onNext(GeneratePDFReply reply) {
                    blackhole.consume(reply);
                    if (++received == requestCount) {
                        latch.countDown();
                    }
                }

                @Override
                public void onError(Throwable t) {
                    blackhole.consume(t);
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            };

            var requestObserver = state.asyncStub.streamPDFs(responseObserver);

            try {
                for (int i = 0; i < requestCount; i++) {
                    var request = GeneratePDFRequest.newBuilder()
                            .setFirstName("Mixed" + i)
                            .setLastName("Test" + i)
                            .build();
                    requestObserver.onNext(request);
                }
                requestObserver.onCompleted();
                latch.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                blackhole.consume(e);
            }
        }
    }
}