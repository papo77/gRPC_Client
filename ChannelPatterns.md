# System.Threading.Channels with gRPC Bi-directional Streaming

## Overview

This implementation demonstrates advanced patterns for using `System.Threading.Channels` with gRPC bi-directional streaming to achieve high-performance, multi-threaded communication.

## Key Features Implemented

### 1. **Bounded Channels with Backpressure**
```csharp
var requestChannel = Channel.CreateBounded<GeneratePDFRequest>(new BoundedChannelOptions(1000)
{
    FullMode = BoundedChannelFullMode.Wait,
    SingleReader = false,
    SingleWriter = false
});
```

- **Backpressure Control**: Bounded channels prevent memory exhaustion by limiting queue size
- **Flow Control**: When channel is full, writers wait instead of consuming unlimited memory
- **Multi-threaded Access**: Configured for multiple readers/writers for better concurrency

### 2. **Concurrent Processing Pipeline**

#### Client-Side Architecture:
```
CSV File → Request Channel → gRPC Request Stream
                ↓
gRPC Response Stream → Response Channel → PDF Writer
```

#### Server-Side Architecture:
```
gRPC Request Stream → Request Channel → Parallel Processing → Response Channel → gRPC Response Stream
```

### 3. **Error Handling & Cancellation**
- **Graceful Cancellation**: Proper `CancellationToken` propagation through all operations
- **Exception Isolation**: Errors in one request don't stop the entire pipeline
- **Resource Cleanup**: Proper disposal of channels and streams

### 4. **Performance Optimizations**

#### Zero-Allocation String Processing:
```csharp
ReadOnlySpan<char> lineSpan = line.AsSpan().Trim();
int commaIndex = lineSpan.IndexOf(',');
ReadOnlySpan<char> lastNameSpan = lineSpan.Slice(0, commaIndex).Trim();
```

#### Parallel Processing:
```csharp
var parallelOptions = new ParallelOptions
{
    MaxDegreeOfParallelism = Environment.ProcessorCount,
    CancellationToken = cancellationToken
};

await Parallel.ForEachAsync(requestChannel.Reader.ReadAllAsync(cancellationToken), 
    parallelOptions, ProcessRequest);
```

## Channel Patterns Used

### 1. **Producer-Consumer Pattern**
- **Producers**: CSV file reader, gRPC stream reader
- **Consumers**: gRPC stream writer, PDF file writer
- **Benefits**: Decouples producers from consumers, enables different processing speeds

### 2. **Pipeline Pattern**
- **Stages**: Read → Queue → Process → Queue → Write
- **Benefits**: Each stage can run at different rates, natural backpressure handling

### 3. **Fan-Out Pattern**
- **Single Input**: Request stream
- **Multiple Processors**: Parallel PDF generation
- **Single Output**: Response stream
- **Benefits**: Maximize CPU utilization, maintain order if needed

## Key Benefits

### 🚀 **Performance**
- **Concurrent Processing**: Multiple PDFs generated simultaneously
- **Memory Efficiency**: Bounded channels prevent memory leaks
- **CPU Utilization**: Parallel processing maximizes hardware usage

### 🛡️ **Reliability**
- **Error Isolation**: One failed request doesn't stop others
- **Graceful Degradation**: System continues processing even with partial failures
- **Proper Cleanup**: Resources are properly disposed

### 📊 **Observability**
- **Metrics Integration**: Custom counters and histograms
- **Distributed Tracing**: Activity sources for end-to-end visibility
- **Structured Logging**: Detailed logging with correlation IDs

## Usage Examples

### Running the Enhanced Client
```bash
cd MakePDFClient
dotnet run
```

### Configuration Options
```json
{
  "ServiceURL": "https://localhost:5555",
  "OutputPath": "./output",
  "writeToDisk": "true"
}
```

## Advanced Scenarios

### Custom Channel Configurations
```csharp
// High-throughput scenario
var channel = Channel.CreateBounded<T>(new BoundedChannelOptions(10000)
{
    FullMode = BoundedChannelFullMode.DropOldest, // Drop old items when full
    SingleReader = true,                          // Single consumer optimization
    SingleWriter = false                          // Multiple producers
});

// Low-latency scenario
var channel = Channel.CreateBounded<T>(new BoundedChannelOptions(100)
{
    FullMode = BoundedChannelFullMode.Wait,       // Backpressure
    SingleReader = false,                         // Multiple consumers
    SingleWriter = true                           // Single producer optimization
});
```

### Custom Processing Patterns
```csharp
// Batch processing
await foreach (var batch in reader.ReadAllAsync().Batch(100))
{
    await ProcessBatch(batch);
}

// Priority queues
var priorityChannel = Channel.CreateUnbounded<(int Priority, Request Item)>();
// Sort by priority before processing
```

## Monitoring & Diagnostics

### Metrics to Monitor
- **Channel Queue Length**: Monitor backpressure
- **Processing Rate**: Items/second through each stage
- **Error Rate**: Failed operations percentage
- **Latency**: End-to-end processing time

### Debugging Tips
- Use structured logging with correlation IDs
- Monitor channel completion events
- Track cancellation token usage
- Watch for deadlocks in bounded channels

This implementation provides a robust, scalable foundation for high-performance gRPC streaming applications using modern .NET concurrency patterns.