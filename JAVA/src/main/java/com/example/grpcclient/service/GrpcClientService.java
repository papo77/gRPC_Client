package com.example.grpcclient.service;

import com.example.grpcclient.config.GrpcClientProperties;
import com.example.grpcclient.proto.GeneratePDFReply;
import com.example.grpcclient.proto.GeneratePDFRequest;
import com.example.grpcclient.proto.MakePDFGrpc;
import com.example.grpcclient.util.ProgressBar;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class GrpcClientService {

    private static final Logger logger = LoggerFactory.getLogger(GrpcClientService.class);

    private final MakePDFGrpc.MakePDFBlockingStub blockingStub;
    private final MakePDFGrpc.MakePDFStub asyncStub;
    private final GrpcClientProperties properties;
    private final ProgressBar progressBar;

    // Concurrent processing components
    private final BlockingQueue<GeneratePDFRequest> requestQueue;
    private final BlockingQueue<GeneratePDFReply> responseQueue;
    private final ExecutorService executorService;
    private final AtomicLong counter = new AtomicLong(0);
    private final AtomicLong numberOfItems = new AtomicLong(0);

    public GrpcClientService(MakePDFGrpc.MakePDFBlockingStub blockingStub,
                           MakePDFGrpc.MakePDFStub asyncStub,
                           GrpcClientProperties properties) {
        this.blockingStub = blockingStub;
        this.asyncStub = asyncStub;
        this.properties = properties;
        this.progressBar = new ProgressBar(properties.showEnhancedProgressBar());
        
        // Initialize concurrent processing components
        this.requestQueue = new LinkedBlockingQueue<>(properties.channelCapacity());
        this.responseQueue = new LinkedBlockingQueue<>();
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void callUnaryMethod() {
        System.out.println("\n=== Testing Unary Method ===");

        try {
            var request = GeneratePDFRequest.newBuilder()
                    .setFirstName("John")
                    .setLastName("Doe")
                    .build();

            var response = blockingStub.generatePDF(request);
            //System.out.println("Received PDF with " + response.getPdf().size() + " bytes");
        } catch (StatusRuntimeException e) {
            logger.error("RPC failed: {}", e.getStatus());
            System.out.println("RPC failed: " + e.getStatus());
        }
    }

    public void callBidirectionalStreaming() {
        System.out.println("\n=== Testing Enhanced Bi-directional Streaming with Concurrent Processing ===");

        Instant startTime = Instant.now();
        var countdown = new CountDownLatch(3); // For request processing, response processing, and PDF writing

        try {
            // Create streaming call
            var responseObserver = new StreamObserver<GeneratePDFReply>() {
                @Override
                public void onNext(GeneratePDFReply reply) {
                    try {
                        responseQueue.put(reply);
                        if (!properties.writeToDisk()) {
                            long currentCount = counter.incrementAndGet();
                            progressBar.updateProgress(currentCount, numberOfItems.get());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.error("Response processing interrupted", e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    logger.error("RPC failed", t);
                    countdown.countDown(); // Signal completion even on error
                }

                @Override
                public void onCompleted() {
                    System.out.println("Response stream completed.");
                    countdown.countDown();
                }
            };

            var requestObserver = asyncStub.streamPDFs(responseObserver);

            // Start concurrent tasks
            CompletableFuture<Void> requestTask = CompletableFuture.runAsync(() -> {
                try {
                    processRequestQueue(requestObserver);
                } finally {
                    countdown.countDown();
                }
            }, executorService);

            CompletableFuture<Void> pdfWriterTask = properties.writeToDisk() ? 
                CompletableFuture.runAsync(() -> {
                    try {
                        processPDFWriter();
                    } finally {
                        countdown.countDown();
                    }
                }, executorService) : 
                CompletableFuture.completedFuture(null);

            // Load and enqueue requests from CSV
            loadRequestsFromCsv();

            // Wait for all tasks to complete (with timeout)
            if (!countdown.await(5, TimeUnit.MINUTES)) {
                logger.warn("Streaming operations timed out");
            }

            requestTask.join();
            if (properties.writeToDisk()) {
                pdfWriterTask.join();
            }

            System.out.println("All streaming operations completed successfully.");

        } catch (Exception e) {
            logger.error("Unexpected error in bidirectional streaming", e);
            System.out.println("Unexpected error: " + e.getMessage());
        }

        Duration elapsed = Duration.between(startTime, Instant.now());
        System.out.printf("Total time taken: %02dh %02dm %02ds %03dms%n",
                elapsed.toHours(),
                elapsed.toMinutesPart(),
                elapsed.toSecondsPart(),
                elapsed.toMillisPart());
    }

    private void processRequestQueue(StreamObserver<GeneratePDFRequest> requestObserver) {
        try {
            while (true) {
                GeneratePDFRequest request = requestQueue.poll(100, TimeUnit.MILLISECONDS);
                if (request == null) {
                    // Check if we're done loading requests
                    if (numberOfItems.get() > 0 && requestQueue.isEmpty()) {
                        break;
                    }
                    continue;
                }

                requestObserver.onNext(request);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Request processing interrupted", e);
        } catch (Exception e) {
            logger.error("Error in request processing", e);
        } finally {
            requestObserver.onCompleted();
            System.out.println("Request stream completed.");
        }
    }

    private void processPDFWriter() {
        if (!properties.writeToDisk()) return;

        Path outputPath = Paths.get(properties.outputPath());
        try {
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            logger.error("Failed to create output directory", e);
            return;
        }

        // Use virtual threads for parallel processing
        List<CompletableFuture<Void>> futures = new ConcurrentLinkedQueue<CompletableFuture<Void>>().stream()
                .limit(properties.maxDegreeOfParallelism())
                .map(i -> CompletableFuture.runAsync(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            GeneratePDFReply reply = responseQueue.poll(100, TimeUnit.MILLISECONDS);
                            if (reply == null) {
                                if (counter.get() >= numberOfItems.get() && numberOfItems.get() > 0) {
                                    break;
                                }
                                continue;
                            }

                            if (!reply.getPdf().isEmpty()) {
                                writePDFToDisk(reply.getPdf().toByteArray(), outputPath);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Exception e) {
                            logger.error("Error processing PDF", e);
                        }
                    }
                }, executorService))
                .toList();

        // Wait for all PDF writers to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void writePDFToDisk(byte[] pdfData, Path outputPath) {
        try {
            String fileName = "pdf_" + UUID.randomUUID() + ".pdf";
            Path filePath = outputPath.resolve(fileName);

            Files.write(filePath, pdfData);
            long currentCount = counter.incrementAndGet();
            progressBar.updateProgress(currentCount, numberOfItems.get());
        } catch (IOException e) {
            logger.error("Error saving PDF", e);
        }
    }

    private void loadRequestsFromCsv() {
        final String csvPath = "./names.csv";
        Path path = Paths.get(csvPath);

        try {
            System.out.println("Loading requests from CSV...");

            if (!Files.exists(path)) {
                System.out.println("CSV file not found at: " + csvPath);
                System.out.println("Adding sample requests instead...");
                addSampleRequests();
                return;
            }

            try (FileReader reader = new FileReader(path.toFile())) {
                var csvFormat = CSVFormat.DEFAULT.builder()
                        .setIgnoreEmptyLines(true)
                        .setTrim(true)
                        .build();

                var csvRecords = csvFormat.parse(reader);

                for (CSVRecord record : csvRecords) {
                    if (record.size() >= 2) {
                        String lastName = record.get(0).trim();
                        String firstName = record.get(1).trim();

                        if (!lastName.isEmpty() && !firstName.isEmpty()) {
                            var request = GeneratePDFRequest.newBuilder()
                                    .setFirstName(firstName)
                                    .setLastName(lastName)
                                    .build();

                            requestQueue.put(request);
                            numberOfItems.incrementAndGet();
                        }
                    }
                }
            }

            System.out.printf("Successfully queued %d requests from CSV.%n", numberOfItems.get());

        } catch (IOException e) {
            logger.error("Error reading CSV file", e);
            System.out.println("Error loading CSV: " + e.getMessage());
            addSampleRequests();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Loading interrupted", e);
        }
    }

    private void addSampleRequests() {
        var sampleRequests = List.of(
                GeneratePDFRequest.newBuilder().setFirstName("Alice").setLastName("Smith").build(),
                GeneratePDFRequest.newBuilder().setFirstName("Bob").setLastName("Johnson").build(),
                GeneratePDFRequest.newBuilder().setFirstName("Charlie").setLastName("Brown").build(),
                GeneratePDFRequest.newBuilder().setFirstName("Diana").setLastName("Wilson").build()
        );

        try {
            for (var request : sampleRequests) {
                requestQueue.put(request);
                numberOfItems.incrementAndGet();
            }
            System.out.println("Added " + sampleRequests.size() + " sample requests.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Sample request loading interrupted", e);
        }
    }
}