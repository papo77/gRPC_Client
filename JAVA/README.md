# Java SpringBoot gRPC Client

A comprehensive Java SpringBoot gRPC client implementation that mirrors the functionality of the .NET gRPC client, supporting both unary calls and bi-directional streaming calls.

## Features

- **Java 21 Support**: Built with LTS Java version and modern features
- **SpringBoot Integration**: Full SpringBoot configuration and dependency injection
- **Unary gRPC Calls**: Simple request-response pattern
- **Bi-directional streaming**: High-performance streaming with concurrent processing
- **CSV Data Processing**: Load names from CSV files for batch processing
- **Virtual Threads**: Leverages Java 21 virtual threads for high concurrency
- **Progress Tracking**: Enhanced visual progress bar with animation
- **SSL Support**: Configurable SSL/TLS support with certificate validation bypass
- **JMH Benchmarking**: Performance benchmarking support using JMH
- **Concurrent Processing**: Channel-based processing similar to .NET implementation

## Project Structure

```
├── pom.xml                              # Maven build configuration
├── src/main/
│   ├── java/com/example/grpcclient/
│   │   ├── GrpcClientApplication.java   # Main SpringBoot application
│   │   ├── config/
│   │   │   ├── GrpcClientProperties.java    # Configuration properties
│   │   │   └── GrpcConfiguration.java       # gRPC channel configuration
│   │   ├── service/
│   │   │   └── GrpcClientService.java       # Main gRPC client logic
│   │   ├── util/
│   │   │   └── ProgressBar.java             # Enhanced progress bar utility
│   │   └── benchmark/
│   │       └── GrpcBenchmarks.java          # JMH benchmarks
│   ├── proto/
│   │   └── MakePDF.proto                # Protocol buffer definition
│   └── resources/
│       └── application.yml              # SpringBoot configuration
├── names.csv                            # Sample CSV data
└── README.md                           # This file
```

## Requirements

- Java 21 (LTS version recommended)
- Maven 3.9.x or later
- gRPC server running on the configured URL

## Building the Project

```bash
# Compile the project (includes protobuf generation)
mvn clean compile

# Package the application
mvn clean package

# Install dependencies and compile
mvn clean install
```

## Running the Application

### Standard Execution
```bash
# Run with Maven
mvn spring-boot:run

# Run the packaged JAR
java -jar target/grpc-client-1.0.0.jar
```

### Benchmark Execution
```bash
# Run benchmarks
java -jar target/grpc-client-1.0.0.jar benchmark

# Or with Maven
mvn spring-boot:run -Dspring-boot.run.arguments=benchmark
```

## Configuration

Configuration is managed through `application.yml`:

```yaml
grpc:
  client:
    service-url: https://primus.local:5555        # gRPC server URL
    output-path: /Volumes/HotRod/output           # PDF output directory
    write-to-disk: false                          # Whether to write PDFs to disk
    show-enhanced-progress-bar: true              # Enable progress visualization
    channel-capacity: 1000                       # Request queue capacity
    max-degree-of-parallelism: 8                  # Concurrent processing threads
    progress-update-threshold: 10                 # Progress update frequency
```

### Environment Variables

You can override configuration using environment variables:
- `GRPC_SERVICE_URL`: Override the service URL
- `GRPC_OUTPUT_PATH`: Override the output path
- `GRPC_MAX_PARALLELISM`: Override maximum parallelism

## Key Features

### Virtual Threads (Java 21)
The application leverages Java 21's virtual threads for high-concurrency operations:
- Request processing
- Response handling
- PDF file writing

### Concurrent Processing Architecture
Similar to the .NET version, this implements:
- **Request Queue**: Bounded blocking queue for request queueing
- **Response Queue**: Unbounded queue for response processing
- **Parallel PDF Writing**: Concurrent file I/O operations
- **Progress Tracking**: Thread-safe progress reporting

### Enhanced Progress Bar
Includes a sophisticated progress bar with:
- Unicode box drawing characters
- Animated spinner
- Percentage and throughput display
- Automatic console width detection

### Bi-directional Streaming
The streaming implementation features:
- StreamObserver pattern for async operations
- Automatic backpressure handling
- Graceful error handling and recovery
- Configurable timeouts

## Protocol Buffer Definition

The service defines two operations:
```protobuf
service MakePDF {
  rpc GeneratePDF (GeneratePDFRequest) returns (GeneratePDFReply);
  rpc StreamPDFs (stream GeneratePDFRequest) returns (stream GeneratePDFReply);
}
```

## CSV Data Format

The application expects CSV data in the format:
```
LastName,FirstName
Smith,John
Doe,Jane
```

## Performance Benchmarking

Run JMH benchmarks to measure performance:
```bash
# Run all benchmarks
mvn exec:exec

# Run specific benchmark
mvn exec:exec -Dexec.args="GrpcBenchmarks.benchmarkUnaryCall"
```

## SSL/TLS Configuration

The client automatically detects HTTPS URLs and configures SSL. For development/testing, certificate validation is disabled by default.

## Comparison with .NET Version

| Feature | .NET Implementation | Java Implementation |
|---------|-------------------|-------------------|
| Concurrency | Channels + Tasks | BlockingQueues + Virtual Threads |
| Configuration | appsettings.json | application.yml |
| Progress Bar | StringBuilder | StringBuilder with Unicode |
| CSV Processing | StreamReader | Apache Commons CSV |
| Benchmarking | BenchmarkDotNet | JMH |
| Async Patterns | async/await | CompletableFuture + StreamObserver |

## Troubleshooting

### Common Issues
1. **Java 25 Not Found**: Ensure Java 25 is installed and JAVA_HOME is set
2. **Preview Features**: Make sure `--enable-preview` flag is used
3. **SSL Errors**: Check if the gRPC server certificate is properly configured
4. **CSV Not Found**: Ensure `names.csv` is in the working directory

### Debug Mode
Enable debug logging by setting:
```yaml
logging:
  level:
    com.example.grpcclient: DEBUG
    io.grpc: DEBUG
```

## Dependencies

Major dependencies include:
- Spring Boot 3.4.0
- gRPC Java 1.68.0
- Protocol Buffers 4.29.1
- Apache Commons CSV 1.12.0
- JMH 1.37

## License

This project follows the same licensing terms as the original .NET implementation.