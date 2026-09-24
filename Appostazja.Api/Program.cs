using System.Net;
using ApostasyMap.Api.Configuration;
using ApostasyMap.Api.Data;
using ApostasyMap.Api.Endpoints;
using ApostasyMap.Api.Json;
using ApostasyMap.Api.Services;

var builder = WebApplication.CreateSlimBuilder(args);

var settings = AppSettings.FromConfiguration(builder.Configuration, builder.Environment.ContentRootPath);
builder.Services.AddSingleton(settings);

builder.Services.ConfigureHttpJsonOptions(options =>
{
    options.SerializerOptions.TypeInfoResolverChain.Insert(0, AppJsonSerializerContext.Default);
});

builder.Services.AddSingleton(_ =>
{
    var handler = new SocketsHttpHandler
    {
        AutomaticDecompression = DecompressionMethods.All,
        PooledConnectionLifetime = TimeSpan.FromMinutes(10)
    };

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

var memoryCache = app.Services.GetRequiredService<MapMemoryCache>();
memoryCache.Replace(store.GetState(), store.LoadPlaces());

if (memoryCache.GetState().PlaceCount == 0)
{
    try
    {
        await app.Services.GetRequiredService<MapRefreshService>()
            .RefreshAsync(force: false, CancellationToken.None);
    }
    catch (Exception exception)
    {
        app.Logger.LogWarning(exception, "Initial source refresh failed; starting with an empty cache.");
    }
}

app.MapGet("/health", () => Results.Text("ok"));
app.MapMapEndpoints();

app.Run();
