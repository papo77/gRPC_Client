using BenchmarkDotNet.Attributes;
using BenchmarkDotNet.Configs;
using BenchmarkDotNet.Jobs;
using BenchmarkDotNet.Toolchains.InProcess.Emit;
using Grpc.Core;
using Grpc.Net.Client;
using MakePDF;
using Microsoft.Extensions.Configuration;
using System.Net.Http;

[MemoryDiagnoser]
[SimpleJob(RuntimeMoniker.Net80)]
[SimpleJob(RuntimeMoniker.Net90)]
[MinColumn, MaxColumn, MeanColumn, MedianColumn]
[MarkdownExporterAttribute.GitHub]
[HtmlExporter]
public class GrpcBenchmarks
{
    private MakePDF.MakePDF.MakePDFClient? _client;
    private GrpcChannel? _channel;
    private string? _serviceUrl;

    [GlobalSetup]
    public void Setup()
    {
        // Load configuration
        var builder = new ConfigurationBuilder()
            .SetBasePath(Directory.GetCurrentDirectory())
            .AddJsonFile("appsettings.json", true, true);
        IConfiguration config = builder.Build();

        _serviceUrl = config["ServiceURL"] ?? throw new Exception("ServiceURL is not configured in appsettings.json");

        // Configure HttpClientHandler to ignore certificate validation
        var httpHandler = new HttpClientHandler();
        httpHandler.ServerCertificateCustomValidationCallback =
            (message, certificate, chain, errors) => true;

        // Create channel and client
        _channel = GrpcChannel.ForAddress(_serviceUrl, new GrpcChannelOptions { HttpHandler = httpHandler });
        _client = new MakePDF.MakePDF.MakePDFClient(_channel);
    }

    [GlobalCleanup]
    public async Task Cleanup()
    {
        if (_channel != null)
        {
            await _channel.ShutdownAsync();
            _channel.Dispose();
        }
    }

    [Benchmark(Description = "Unary gRPC Method Call")]
    public async Task<GeneratePDFReply> BenchmarkUnaryMethod()
    {
        if (_client == null) throw new InvalidOperationException("Client not initialized");

        var request = new GeneratePDFRequest
        {
            FirstName = "John",
            LastName = "Doe"
        };

        return await _client.GeneratePDFAsync(request);
    }

    [Benchmark(Description = "Bidirectional Streaming gRPC Method")]
    public async Task BenchmarkBidirectionalStreaming()
    {
        if (_client == null) throw new InvalidOperationException("Client not initialized");

        using var cts = new CancellationTokenSource();
        using var call = _client.StreamPDFs(cancellationToken: cts.Token);

        try
        {
            // Send a few test requests
            var requests = new[]
            {
                new GeneratePDFRequest { FirstName = "Alice", LastName = "Smith" },
                new GeneratePDFRequest { FirstName = "Bob", LastName = "Johnson" },
                new GeneratePDFRequest { FirstName = "Charlie", LastName = "Brown" }
            };

            // Start response reading task
            var responseTask = Task.Run(async () =>
            {
                var responseCount = 0;
                await foreach (var response in call.ResponseStream.ReadAllAsync(cts.Token))
                {
                    responseCount++;
                    if (responseCount >= requests.Length)
                        break;
                }
            }, cts.Token);

            // Send requests
            foreach (var request in requests)
            {
                await call.RequestStream.WriteAsync(request, cts.Token);
            }
            
            // Complete the request stream
            await call.RequestStream.CompleteAsync();

            // Wait for all responses
            await responseTask;
        }
        catch (RpcException ex) when (ex.StatusCode == StatusCode.Cancelled)
        {
            // Expected when cancellation token is triggered
        }
        catch (OperationCanceledException)
        {
            // Expected when cancellation token is triggered
        }
    }

    [Benchmark(Description = "Unary Method with Multiple Concurrent Calls")]
    [Arguments(5)]
    [Arguments(10)]
    [Arguments(25)]
    public async Task BenchmarkConcurrentUnaryMethods(int concurrentCalls)
    {
        if (_client == null) throw new InvalidOperationException("Client not initialized");

        var tasks = new Task[concurrentCalls];
        
        for (int i = 0; i < concurrentCalls; i++)
        {
            var request = new GeneratePDFRequest
            {
                FirstName = $"User{i}",
                LastName = "Test"
            };
            
            tasks[i] = _client.GeneratePDFAsync(request).ResponseAsync;
        }

        await Task.WhenAll(tasks);
    }

    [Benchmark(Description = "Single Streaming Call with Multiple Requests")]
    [Arguments(5)]
    [Arguments(10)]
    [Arguments(25)]
    public async Task BenchmarkStreamingWithMultipleRequests(int requestCount)
    {
        if (_client == null) throw new InvalidOperationException("Client not initialized");

        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(30)); // Timeout for safety
        using var call = _client.StreamPDFs(cancellationToken: cts.Token);

        try
        {
            // Start response reading task
            var responseTask = Task.Run(async () =>
            {
                var responseCount = 0;
                await foreach (var response in call.ResponseStream.ReadAllAsync(cts.Token))
                {
                    responseCount++;
                    if (responseCount >= requestCount)
                        break;
                }
            }, cts.Token);

            // Send requests
            for (int i = 0; i < requestCount; i++)
            {
                var request = new GeneratePDFRequest
                {
                    FirstName = $"StreamUser{i}",
                    LastName = "Test"
                };
                await call.RequestStream.WriteAsync(request, cts.Token);
            }
            
            // Complete the request stream
            await call.RequestStream.CompleteAsync();

            // Wait for all responses
            await responseTask;
        }
        catch (RpcException ex) when (ex.StatusCode == StatusCode.Cancelled)
        {
            // Expected when cancellation token is triggered
        }
        catch (OperationCanceledException)
        {
            // Expected when cancellation token is triggered
        }
    }
}