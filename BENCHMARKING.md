# gRPC Benchmarking with BenchmarkDotNet

This project now includes comprehensive benchmarking capabilities using BenchmarkDotNet to measure the performance of your gRPC client operations.

## Available Benchmarks

### 1. Unary Method Benchmark
- **Method**: `BenchmarkUnaryMethod`
- **Description**: Measures the performance of single unary gRPC calls
- **What it tests**: CallUnaryMethod performance with a single request/response

### 2. Bidirectional Streaming Benchmark
- **Method**: `BenchmarkBidirectionalStreaming`
- **Description**: Measures the performance of bidirectional streaming gRPC calls
- **What it tests**: CallBidirectionalStreaming performance with multiple requests/responses

### 3. Concurrent Unary Methods Benchmark
- **Method**: `BenchmarkConcurrentUnaryMethods`
- **Description**: Measures performance when making multiple concurrent unary calls
- **Parameters**: Tests with 5, 10, and 25 concurrent calls
- **What it tests**: Scalability and concurrent performance of unary operations

### 4. Streaming with Multiple Requests Benchmark
- **Method**: `BenchmarkStreamingWithMultipleRequests`
- **Description**: Measures performance of a single streaming connection with multiple requests
- **Parameters**: Tests with 5, 10, and 25 requests per stream
- **What it tests**: Throughput performance of streaming operations

## How to Run Benchmarks

### 1. Build the Project
```bash
dotnet build -c Release
```

### 2. Run Benchmarks
```bash
dotnet run -c Release -- benchmark
```

### 3. Alternative: Run Specific Benchmark Categories
You can also run the executable directly:
```bash
./bin/Release/net10.0/MakePDFClient benchmark
```

## Benchmark Results

BenchmarkDotNet will generate detailed results including:
- **Mean**: Average execution time
- **Median**: Middle value when results are sorted
- **Min/Max**: Fastest and slowest execution times
- **Memory Allocation**: Amount of memory allocated during execution
- **Standard Deviation**: Measure of result consistency

Results are automatically exported to:
- **Console**: Real-time results display
- **HTML Report**: Detailed HTML report with charts
- **Markdown**: GitHub-compatible markdown table

## Configuration

Make sure your `appsettings.json` is properly configured with:
```json
{
  "ServiceURL": "https://your-grpc-service-url",
  "OutputPath": "./output",
  "writeToDisk": false
}
```

**Note**: For benchmarking, it's recommended to set `writeToDisk` to `false` to avoid I/O overhead affecting the gRPC performance measurements.

## Benchmark Framework Features

The benchmarks include:
- **Memory diagnostics** to track allocations
- **Multiple .NET runtime targets** (NET 8.0 and NET 10.0)
- **Proper setup/cleanup** for gRPC connections
- **Error handling** for network timeouts and cancellations
- **Parameterized tests** for scalability analysis

## Tips for Accurate Benchmarking

1. **Run in Release mode**: Always use `-c Release` for accurate performance measurements
2. **Close unnecessary applications**: Minimize background processes that could affect results
3. **Warm up period**: BenchmarkDotNet automatically includes warm-up iterations
4. **Network stability**: Ensure stable network connection to your gRPC service
5. **Multiple runs**: BenchmarkDotNet automatically runs multiple iterations for statistical accuracy

## Regular Usage

To run the application normally (not benchmarks):
```bash
dotnet run
```

This will execute the standard gRPC client functionality without benchmarking overhead.