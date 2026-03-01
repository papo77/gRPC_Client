using System.Net.Cache;
using System.Threading.Channels;
using System.Text;
using Grpc.Core;
using Grpc.Net.Client;
using MakePDF;
using Microsoft.Extensions.Configuration;
using BenchmarkDotNet.Running;
using BenchmarkDotNet.Attributes;

class Program
{
    private static string? outputFolder = string.Empty;
    private static bool writeToDisk = false;

    private static long counter;
    private static long numberOfItems;

    // Enhanced channel setup with bounded capacity for backpressure
    private static readonly Channel<GeneratePDFRequest> RequestChannel = Channel.CreateBounded<GeneratePDFRequest>(new BoundedChannelOptions(1000)
    {
        FullMode = BoundedChannelFullMode.Wait,
        SingleReader = false,
        SingleWriter = false
    });

    // Channel for handling PDF responses
    private static readonly Channel<GeneratePDFReply> ResponseChannel = Channel.CreateUnbounded<GeneratePDFReply>();

    // Enhanced processing with proper cancellation and error handling
    static async Task ProcessRequestChannel(ChannelReader<GeneratePDFRequest> reader, IClientStreamWriter<GeneratePDFRequest> streamWriter, CancellationToken cancellationToken)
    {
        try
        {
            await foreach (var request in reader.ReadAllAsync(cancellationToken))
            {
                //Console.WriteLine($"Sending PDF request for {request.FirstName} {request.LastName}");
                await streamWriter.WriteAsync(request, cancellationToken);
            }
        }
        catch (OperationCanceledException)
        {
            Console.WriteLine("Request processing was cancelled.");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error in request processing: {ex.Message}");
        }
        finally
        {
            await streamWriter.CompleteAsync();
            Console.WriteLine("Request stream completed.");
        }
    }

    // Enhanced response processing with channels
    static async Task ProcessResponseChannel(IAsyncStreamReader<GeneratePDFReply> responseStream, ChannelWriter<GeneratePDFReply> responseWriter, CancellationToken cancellationToken)
    {
        try
        {
            await foreach (var response in responseStream.ReadAllAsync(cancellationToken))
            {
                //Console.WriteLine($"Received PDF response with {response.Pdf.Length:n0} bytes");

                // Write to channel for further processing
                await responseWriter.WriteAsync(response, cancellationToken);

            if(!writeToDisk)
                {
                    Interlocked.Increment(ref counter);
                    ((IProgress<(long, long)>)progress).Report((counter, numberOfItems));
                }
            }
        }
        catch (OperationCanceledException)
        {
            Console.WriteLine("Response processing was cancelled.");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error in response processing: {ex.Message}");
        }
        finally
        {
            responseWriter.Complete();
            Console.WriteLine("Response stream processing completed.");
        }
    }

    // Concurrent PDF file writer using channels
    static async Task ProcessPDFWriter(ChannelReader<GeneratePDFReply> reader,
        CancellationToken cancellationToken)
    {
        // Process PDFs concurrently with limited parallelism
        var parallelOptions = new ParallelOptions
        {
            MaxDegreeOfParallelism = Environment.ProcessorCount,
            CancellationToken = cancellationToken
        };

        try
        {
            await Parallel.ForEachAsync(reader.ReadAllAsync(cancellationToken), parallelOptions,
                async (response, token) =>
                {
                    if (response.Pdf?.Length > 0)
                    {
                        await WritePDFToDisk(response.Pdf.ToByteArray(), token);
                    }
                });
        }
        catch (OperationCanceledException)
        {
            Console.WriteLine("PDF writing was cancelled.");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error in PDF writing: {ex.Message}");
        }
    }

    static async Task WritePDFToDisk(byte[] pdfData, CancellationToken cancellationToken = default)
    {
        if (!writeToDisk || string.IsNullOrEmpty(outputFolder)) return;

        try
        {
            string fileName = $"pdf_{Guid.NewGuid()}.pdf";
            string filePath = Path.Combine(outputFolder!, fileName);

            await File.WriteAllBytesAsync(filePath, pdfData, cancellationToken);
            //Console.WriteLine($"Saved PDF: {fileName}");
            Interlocked.Increment(ref counter);
            ((IProgress<(long, long)>)progress).Report((counter, numberOfItems));
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error saving PDF: {ex.Message}");
        }
    }

    static async Task Main(string[] args)
    {
        // Check if benchmark argument is provided
        if (args.Length > 0 && args[0].Equals("benchmark", StringComparison.OrdinalIgnoreCase))
        {
            Console.WriteLine("Running benchmarks...");
            BenchmarkRunner.Run<GrpcBenchmarks>();
            return;
        }

        var builder = new ConfigurationBuilder()
            .SetBasePath(Directory.GetCurrentDirectory())
            .AddJsonFile("appsettings.json", true, true);
        IConfiguration config = builder.Build();

        string? serviceUrl = String.IsNullOrEmpty(config["ServiceURL"]) == false ? config["ServiceURL"] : throw new Exception("ServiceURL is not configured in appsettings.json");
        outputFolder = String.IsNullOrEmpty(config["OutputPath"]) == false ? config["OutputPath"] : Path.Combine(Directory.GetCurrentDirectory(), "output");
        writeToDisk = bool.TryParse(config["writeToDisk"], out var result) ? result : false;
        if (String.IsNullOrEmpty(config["ShowEnhancedProgressBar"]) == false)
        {
            bool.TryParse(config["ShowEnhancedProgressBar"], out shouldUseEnhancedProgressBar);
        }



        Console.WriteLine("Starting bi-directional streaming gRPC client...");

        // Configure HttpClientHandler to ignore certificate validation
        var httpHandler = new HttpClientHandler();
        httpHandler.ServerCertificateCustomValidationCallback =
            (message, certificate, chain, errors) => true;

        // The port number should match the server port.
        if (String.IsNullOrEmpty(serviceUrl))
        {
            Console.WriteLine("Service URL is not configured. Please set the ServiceURL in appsettings.json.");
            return;
        }

        using var channel = GrpcChannel.ForAddress(serviceUrl, new GrpcChannelOptions { HttpHandler = httpHandler });

        var client = new MakePDF.MakePDF.MakePDFClient(channel);

        // Test both unary and bi-directional streaming
        await CallUnaryMethod(client);
        await CallBidirectionalStreaming(client);


        Console.WriteLine("Press any key to exit...");
        Console.ReadKey();
    }

    public static async Task CallUnaryMethod(MakePDF.MakePDF.MakePDFClient client)
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
            Console.WriteLine($"RPC failed: {ex.ToString()}");
        }
    }

    public static async Task CallBidirectionalStreaming(MakePDF.MakePDF.MakePDFClient client)
    {
        Console.WriteLine("\n=== Testing Enhanced Bi-directional Streaming with Channels ===");

        using var cts = new CancellationTokenSource();
        using var call = client.StreamPDFs(cancellationToken: cts.Token);

        var sw = System.Diagnostics.Stopwatch.StartNew();

        try
        {
            // Start concurrent tasks for processing
            var requestTask = ProcessRequestChannel(RequestChannel.Reader, call.RequestStream, cts.Token);
            var responseTask = ProcessResponseChannel(call.ResponseStream, ResponseChannel.Writer, cts.Token);
            var pdfWriterTask = writeToDisk ? ProcessPDFWriter(ResponseChannel.Reader, cts.Token) : Task.CompletedTask;

            // Load and enqueue requests from CSV
            await LoadRequestsFromCsv();

            // Wait for request processing to complete
            await requestTask;

            // Wait for response processing to complete
            await responseTask;

            // Wait for PDF writing to complete
            if (writeToDisk)
            {
                await pdfWriterTask;
            }

            Console.WriteLine("All streaming operations completed successfully.");
        }
        catch (RpcException ex)
        {
            Console.WriteLine($"RPC failed: {ex.Status}");
            cts.Cancel(); // Cancel remaining operations
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Unexpected error: {ex.Message}");
            cts.Cancel(); // Cancel remaining operations
        }

        sw.Stop();
        var elapsed = sw.Elapsed;
        Console.WriteLine($"Total time taken: {elapsed.Hours:D2}h {elapsed.Minutes:D2}m {elapsed.Seconds:D2}s {elapsed.Milliseconds:D3}ms");
    }

    private static async Task LoadRequestsFromCsv()
    {
        const string path = "./names.csv";

        try
        {
            Console.WriteLine("Loading requests from CSV...");

            // Use more efficient file reading
            using var fileStream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read, bufferSize: 4096);
            using var reader = new StreamReader(fileStream, Encoding.UTF8);

            string? line;


            while ((line = await reader.ReadLineAsync()) != null)
            {
                if (string.IsNullOrWhiteSpace(line)) continue;

                // Use ReadOnlySpan<char> for zero-allocation string operations
                ReadOnlySpan<char> lineSpan = line.AsSpan().Trim();

                int commaIndex = lineSpan.IndexOf(',');
                if (commaIndex > 0 && commaIndex < lineSpan.Length - 1)
                {
                    // Extract parts using spans (zero allocation)
                    ReadOnlySpan<char> lastNameSpan = lineSpan.Slice(0, commaIndex).Trim();
                    ReadOnlySpan<char> firstNameSpan = lineSpan.Slice(commaIndex + 1).Trim();

                    if (!lastNameSpan.IsEmpty && !firstNameSpan.IsEmpty)
                    {
                        var request = new GeneratePDFRequest
                        {
                            FirstName = firstNameSpan.ToString(),
                            LastName = lastNameSpan.ToString()
                        };

                        // Write to channel with backpressure handling
                        await RequestChannel.Writer.WriteAsync(request);
                        Interlocked.Increment(ref numberOfItems);


                        // Progress reporting every 100 items
                        // if (numberOfItems % 100 == 0)
                        // {
                        //     Console.WriteLine($"Queued {numberOfItems} requests...");
                        // }
                    }
                }
            }

            Console.WriteLine($"Successfully queued {numberOfItems} requests from CSV.");
        }
        catch (FileNotFoundException)
        {
            Console.WriteLine($"CSV file not found at: {path}");
            Console.WriteLine("Adding sample requests instead...");

            // Add sample requests if file doesn't exist
            var sampleRequests = new[]
            {
                new GeneratePDFRequest { FirstName = "Alice", LastName = "Smith" },
                new GeneratePDFRequest { FirstName = "Bob", LastName = "Johnson" },
                new GeneratePDFRequest { FirstName = "Charlie", LastName = "Brown" },
                new GeneratePDFRequest { FirstName = "Diana", LastName = "Wilson" }
            };

            foreach (var request in sampleRequests)
            {
                await RequestChannel.Writer.WriteAsync(request);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error loading CSV: {ex.Message}");
        }
        finally
        {
            // Signal that we're done adding requests
            RequestChannel.Writer.Complete();
            Console.WriteLine("Request loading completed.");
        }
    }


    private static int spinnerIndex = 0;
    private static readonly char[] spikeAnimation = { '|', '/', '-', '\\' };
    private static readonly char[] quarterCircleAnimation = { (char)9716, (char)9719, (char)9718, (char)9717 }; // ◴, ◷, ◶, ◵
    private static readonly char[] circleSegmentAnimation = { (char)9692, (char)9696, (char)9693, (char)9694, (char)9697, (char)9695 }; // ◜, ◠,◝, ◞,◡, ◟
    private static readonly char[] halfCircleAnimation = { (char)9680, (char)9683, (char)9681, (char)9682 }; // ◐, ◓, ◑, ◒

    //..circleSegmentAnimation, ..quarterCircleAnimation,
    private static readonly char[] animationChars = [.. halfCircleAnimation];
    private static Progress<(long processed, long total)> progress = new Progress<(long processed, long total)>(tuple =>
    {
        var (processed, total) = tuple;
        DrawProgressBar(processed, total);
    });

    static int consoleWidth = Console.WindowWidth;
    static StringBuilder progressBar = new System.Text.StringBuilder(consoleWidth * 3);
    const char fillChar = (char)9608;
    const char emptyChar = (char)9617; //9617;
    const char leftChar = (char)9474;
    const char rightChar = (char)9474;
    const char upperLeftChar = (char)9484;
    const char upperRightChar = (char)9488;
    const char lowerLeftChar = (char)9492;
    const char lowerRightChar = (char)9496;
    const char horizontalChar = (char)9472;

    private static long lastProgressUpdate = 0;
    private static readonly object progressLock = new object();

    private static bool shouldUseEnhancedProgressBar = true;
    private static void DrawProgressBar(long progress, long total)
    {
        if (!shouldUseEnhancedProgressBar)
        {
            return;
        }

        //https://www.w3schools.com/charsets/ref_utf_box.asp 
        if ((progress != total) && (progress - lastProgressUpdate) < 10)
            return;

        if (Monitor.TryEnter(progressLock, 0))
        {
            try
            {
                lastProgressUpdate = progress;
                var percent = (int)((double)progress / total * 100);

                var statusString = $"{percent}% {progress:N0} / {total:N0} {animationChars[spinnerIndex]}";



                var barSize = Console.WindowWidth - statusString.Length - 10;
                var progressSize = (int)((double)progress / total * barSize);
                progressBar.Clear();


                int curX, curY;
                int x = 0;
                int y = 0;

                if (spinnerIndex == animationChars.Length - 1)
                    spinnerIndex = 0;
                else
                    spinnerIndex++;


                var progressLine = new System.Text.StringBuilder();
                progressLine.Append(leftChar);
                progressLine.Append(fillChar, progressSize);
                progressLine.Append(emptyChar, barSize - progressSize);
                progressLine.Append($"{rightChar} {statusString}");
                progressLine.Append(new string(' ', consoleWidth - progressLine.Length - 2)); // Padding to clear any leftover characters
                progressLine.Append('\n');

                //Top border
                progressBar.Append(upperLeftChar);
                progressBar.Append(horizontalChar, barSize);
                progressBar.Append(upperRightChar);
                progressBar.Append(new string(' ', consoleWidth - barSize - 2)); // Padding to clear any leftover characters
                progressBar.Append('\n');

                //Progress line
                progressBar.Append(progressLine.ToString());

                //Bottom border
                progressBar.Append(lowerLeftChar); 
                progressBar.Append(horizontalChar, barSize);
                progressBar.Append(lowerRightChar);
                progressBar.Append(new string(' ', consoleWidth - barSize - 3)); // Padding to clear any leftover characters

                (curX, curY) = Console.GetCursorPosition();

                Console.SetCursorPosition(x, y);
                //Console.CursorLeft = 0; // Reset cursor to start of line        
                Console.Write(progressBar.ToString());
                Console.SetCursorPosition(curX, curY);
            }
            finally
            {
                Monitor.Exit(progressLock);
            }
        }

    }


}
