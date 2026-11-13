using System.Net;
using System.Runtime.CompilerServices;
using ApostasyMap.Api.Configuration;
using ApostasyMap.Api.Data;
using ApostasyMap.Api.Endpoints;
using ApostasyMap.Api.Json;
using ApostasyMap.Api.Services;

[assembly: InternalsVisibleTo("Appostazja.Api.Tests")]

var builder = WebApplication.CreateSlimBuilder(args);

var settings = AppSettings.FromConfiguration(builder.Configuration, builder.Environment.ContentRootPath);
builder.Services.AddSingleton(settings);
builder.Services.AddSingleton(TimeProvider.System);

builder.Services.ConfigureHttpJsonOptions(options =>
{
    options.SerializerOptions.TypeInfoResolverChain.Insert(0, AppJsonSerializerContext.Default);
});
builder.Services.AddOpenApi();

builder.Services.AddSingleton(_ =>
{
    var handler = new SocketsHttpHandler
    {
        AutomaticDecompression = DecompressionMethods.All,
        PooledConnectionLifetime = TimeSpan.FromMinutes(10)
    };

    // Response size is limited while streaming in MapRefreshService (Source:MaxResponseMegabytes).
    var client = new HttpClient(handler)
    {
        Timeout = TimeSpan.FromSeconds(settings.Source.RequestTimeoutSeconds)
    };
    client.DefaultRequestHeaders.UserAgent.ParseAdd("ApostasyMap.Api/1.0");
    return client;
});

builder.Services.AddSingleton<MapStore>();
builder.Services.AddSingleton<MapMemoryCache>();
builder.Services.AddSingleton<MapRefreshService>();
builder.Services.AddHostedService<NightlyRefreshWorker>();

var app = builder.Build();

var store = app.Services.GetRequiredService<MapStore>();
store.Initialize();
// Databases created by older versions cached the full responses CSV, including private columns.
if (store.RewriteInput(settings.Source.Url, ResponsesCsv.Sanitize))
{
    app.Logger.LogInformation("Removed private columns from the cached responses CSV.");
}

var memoryCache = app.Services.GetRequiredService<MapMemoryCache>();
memoryCache.Replace(store.GetState(), store.LoadPlaces(), store.LoadReports());

app.MapHealthEndpoints();
app.MapOpenApi("/openapi/v1.json");
app.MapMapEndpoints();

app.Run();
