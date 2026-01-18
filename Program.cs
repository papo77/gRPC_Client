using Grpc.Core;
using Grpc.Net.Client;
using MakePDF;

namespace MakePDFClient;

class Program
{
    static async Task Main(string[] args)
    {
        Console.WriteLine("Starting bi-directional streaming gRPC client...");
        
        // The port number should match the server port.
        using var channel = GrpcChannel.ForAddress("http://localhost:5000");
        var client = new MakePDF.MakePDF.MakePDFClient(channel);

        // Test both unary and bi-directional streaming
        await CallUnaryMethod(client);
        await CallBidirectionalStreaming(client);
        
        Console.WriteLine("Press any key to exit...");
        Console.ReadKey();
    }

    private static async Task CallUnaryMethod(MakePDF.MakePDF.MakePDFClient client)
    {
        Console.WriteLine("\n=== Testing Unary Method ===");
        
        try
        {
            var request = new GeneratePDFRequest
            {
                FirstName = "John",
                LastName = "Doe"
            };

            var response = await client.GeneratePDFAsync(request);
            Console.WriteLine($"Received PDF with {response.Pdf.Length} bytes");
        }
        catch (RpcException ex)
        {
            Console.WriteLine($"RPC failed: {ex.Status}");
        }
    }

    private static async Task CallBidirectionalStreaming(MakePDF.MakePDF.MakePDFClient client)
    {
        Console.WriteLine("\n=== Testing Bi-directional Streaming ===");
        
        try
        {
            using var call = client.StreamPDFs();
            
            // Start a task to read responses
            var readTask = Task.Run(async () =>
            {
                await foreach (var response in call.ResponseStream.ReadAllAsync())
                {
                    Console.WriteLine($"Received PDF response with {response.Pdf.Length} bytes");
                }
                Console.WriteLine("Response stream completed.");
            });

            // Send multiple requests
            var requests = new[]
            {
                new GeneratePDFRequest { FirstName = "Alice", LastName = "Smith" },
                new GeneratePDFRequest { FirstName = "Bob", LastName = "Johnson" },
                new GeneratePDFRequest { FirstName = "Charlie", LastName = "Brown" },
                new GeneratePDFRequest { FirstName = "Diana", LastName = "Wilson" },
            };

            Console.WriteLine("Sending requests...");
            foreach (var request in requests)
            {
                await call.RequestStream.WriteAsync(request);
                Console.WriteLine($"Sent request for: {request.FirstName} {request.LastName}");
                
                // Add some delay between requests to simulate real-world scenario
                //await Task.Delay(1000);
            }

            // Signal that we're done sending requests
            await call.RequestStream.CompleteAsync();
            Console.WriteLine("Completed sending requests.");

            // Wait for all responses to be received
            await readTask;

        }
        catch (RpcException ex)
        {
            Console.WriteLine($"RPC failed: {ex.Status}");
        }
    }
}
