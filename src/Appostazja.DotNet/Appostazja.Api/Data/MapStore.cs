using ApostasyMap.Api.Configuration;
using ApostasyMap.Api.Contracts;
using ApostasyMap.Api.Json;
using Microsoft.Data.Sqlite;
using System.Text.Json;

namespace ApostasyMap.Api.Data;

public sealed class MapStore(AppSettings settings)
{
    private readonly string _connectionString = new SqliteConnectionStringBuilder
    {
        DataSource = settings.Database.Path,
        Mode = SqliteOpenMode.ReadWriteCreate,
        Cache = SqliteCacheMode.Shared,
        Pooling = true
    }.ToString();

    public void Initialize()
    {
        var directory = Path.GetDirectoryName(settings.Database.Path);
        if (!string.IsNullOrWhiteSpace(directory))
        {
            Directory.CreateDirectory(directory);
        }

        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            PRAGMA journal_mode=WAL;
            PRAGMA synchronous=NORMAL;
            PRAGMA busy_timeout=5000;

            CREATE TABLE IF NOT EXISTS places (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                address TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                score REAL NOT NULL,
                ratings_count INTEGER NOT NULL,
                positive_ratings INTEGER NOT NULL,
                neutral_ratings INTEGER NOT NULL,
                negative_ratings INTEGER NOT NULL
            );

            CREATE INDEX IF NOT EXISTS ix_places_lat_lon
                ON places(latitude, longitude);

            CREATE TABLE IF NOT EXISTS place_reports (
                place_id TEXT PRIMARY KEY NOT NULL,
                details_json BLOB NOT NULL,
                aliases_json BLOB NOT NULL
            );

            CREATE TABLE IF NOT EXISTS source_inputs (
                url TEXT PRIMARY KEY NOT NULL,
                payload BLOB NOT NULL,
                etag TEXT NULL,
                last_modified TEXT NULL
            );

            CREATE TABLE IF NOT EXISTS dataset_state (
                id INTEGER PRIMARY KEY NOT NULL CHECK (id = 1),
                source_url TEXT NOT NULL,
                version TEXT NULL,
                last_checked_at_unix_seconds INTEGER NOT NULL,
                last_changed_at_unix_seconds INTEGER NOT NULL,
                place_count INTEGER NOT NULL
            );

            INSERT OR IGNORE INTO dataset_state (
                id,
                source_url,
                version,
                last_checked_at_unix_seconds,
                last_changed_at_unix_seconds,
                place_count
            ) VALUES (1, $sourceUrl, NULL, 0, 0, 0);
            """;
        command.Parameters.AddWithValue("$sourceUrl", settings.Source.Url.ToString());
        command.ExecuteNonQuery();
    }

    public DatasetStateRow GetState()
    {
        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT source_url,
                   version,
                   last_checked_at_unix_seconds,
                   last_changed_at_unix_seconds,
                   place_count,
                   (SELECT url FROM source_inputs WHERE url <> dataset_state.source_url LIMIT 1)
            FROM dataset_state
            WHERE id = 1;
            """;

        using var reader = command.ExecuteReader();
        if (!reader.Read())
        {
            throw new InvalidOperationException("dataset_state is not initialized.");
        }

        return new DatasetStateRow
        {
            SourceUrl = reader.GetString(0),
            Version = reader.IsDBNull(1) ? null : reader.GetString(1),
            LastCheckedAtUnixSeconds = reader.GetInt64(2),
            LastChangedAtUnixSeconds = reader.GetInt64(3),
            PlaceCount = reader.GetInt32(4),
            CoordinatesUrl = reader.IsDBNull(5) ? null : reader.GetString(5)
        };
    }

    public PlaceDto[] LoadPlaces()
    {
        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT id, name, address, latitude, longitude, score,
                   ratings_count, positive_ratings, neutral_ratings, negative_ratings
            FROM places
            ORDER BY id;
            """;

        using var reader = command.ExecuteReader();
        var result = new List<PlaceDto>();
        while (reader.Read())
        {
            result.Add(ReadPlace(reader));
        }

        return [.. result];
    }

    public PlaceReportsRow[] LoadReports()
    {
        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT place_id, details_json, aliases_json FROM place_reports;";
        using var reader = command.ExecuteReader();
        var result = new List<PlaceReportsRow>();
        while (reader.Read())
        {
            var details = JsonSerializer.Deserialize(
                reader.GetFieldValue<byte[]>(1), AppJsonSerializerContext.Default.PlaceReportsDto)!;
            var aliases = JsonSerializer.Deserialize(
                reader.GetFieldValue<byte[]>(2), AppJsonSerializerContext.Default.StringArray)!;
            result.Add(new PlaceReportsRow(reader.GetString(0), aliases, details));
        }

        return [.. result];
    }

    public SourceInputRow? LoadInput(Uri url)
    {
        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT payload, etag, last_modified FROM source_inputs WHERE url = $url;";
        command.Parameters.AddWithValue("$url", url.AbsoluteUri);
        using var reader = command.ExecuteReader();
        return reader.Read()
            ? new SourceInputRow(url, reader.GetFieldValue<byte[]>(0),
                reader.IsDBNull(1) ? null : reader.GetString(1), reader.IsDBNull(2) ? null : reader.GetString(2))
            : null;
    }

    public void MarkChecked(DateTimeOffset checkedAtUtc, IReadOnlyList<SourceInputRow> changedInputs)
    {
        using var connection = OpenConnection();
        using var transaction = connection.BeginTransaction();
        SaveInputs(connection, transaction, changedInputs);
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            UPDATE dataset_state
            SET last_checked_at_unix_seconds = $checkedAt
            WHERE id = 1;
            """;
        command.Parameters.AddWithValue("$checkedAt", checkedAtUtc.ToUnixTimeSeconds());
        command.ExecuteNonQuery();
        transaction.Commit();
    }

    public void ReplaceDataset(
        IReadOnlyList<PlaceRow> places,
        IReadOnlyList<PlaceReportsRow> reports,
        IReadOnlyList<SourceInputRow> inputs,
        string version,
        DateTimeOffset checkedAtUtc)
    {
        using var connection = OpenConnection();
        using var transaction = connection.BeginTransaction();

        try
        {
            using (var delete = connection.CreateCommand())
            {
                delete.Transaction = transaction;
                delete.CommandText = "DELETE FROM places; DELETE FROM place_reports;";
                delete.ExecuteNonQuery();
            }

            using (var insert = connection.CreateCommand())
            {
                insert.Transaction = transaction;
                insert.CommandText = """
                    INSERT INTO places (
                        id, name, address, latitude, longitude, score,
                        ratings_count, positive_ratings, neutral_ratings, negative_ratings
                    ) VALUES (
                        $id, $name, $address, $latitude, $longitude, $score,
                        $ratingsCount, $positiveRatings, $neutralRatings, $negativeRatings
                    );
                    """;

                var id = insert.Parameters.Add("$id", SqliteType.Text);
                var name = insert.Parameters.Add("$name", SqliteType.Text);
                var address = insert.Parameters.Add("$address", SqliteType.Text);
                var latitude = insert.Parameters.Add("$latitude", SqliteType.Real);
                var longitude = insert.Parameters.Add("$longitude", SqliteType.Real);
                var score = insert.Parameters.Add("$score", SqliteType.Real);
                var ratingsCount = insert.Parameters.Add("$ratingsCount", SqliteType.Integer);
                var positiveRatings = insert.Parameters.Add("$positiveRatings", SqliteType.Integer);
                var neutralRatings = insert.Parameters.Add("$neutralRatings", SqliteType.Integer);
                var negativeRatings = insert.Parameters.Add("$negativeRatings", SqliteType.Integer);

                insert.Prepare();

                foreach (var place in places)
                {
                    id.Value = place.Id;
                    name.Value = place.Name;
                    address.Value = place.Address;
                    latitude.Value = place.Latitude;
                    longitude.Value = place.Longitude;
                    score.Value = place.Score;
                    ratingsCount.Value = place.RatingsCount;
                    positiveRatings.Value = place.PositiveRatings;
                    neutralRatings.Value = place.NeutralRatings;
                    negativeRatings.Value = place.NegativeRatings;
                    insert.ExecuteNonQuery();
                }
            }

            using (var insert = connection.CreateCommand())
            {
                insert.Transaction = transaction;
                insert.CommandText = """
                    INSERT INTO place_reports (place_id, details_json, aliases_json)
                    VALUES ($id, $details, $aliases);
                    """;
                var id = insert.Parameters.Add("$id", SqliteType.Text);
                var details = insert.Parameters.Add("$details", SqliteType.Blob);
                var aliases = insert.Parameters.Add("$aliases", SqliteType.Blob);
                insert.Prepare();

                foreach (var report in reports)
                {
                    id.Value = report.PlaceId;
                    details.Value = JsonSerializer.SerializeToUtf8Bytes(
                        report.Details, AppJsonSerializerContext.Default.PlaceReportsDto);
                    aliases.Value = JsonSerializer.SerializeToUtf8Bytes(
                        report.Aliases, AppJsonSerializerContext.Default.StringArray);
                    insert.ExecuteNonQuery();
                }
            }

            SaveInputs(connection, transaction, inputs);
            using (var delete = connection.CreateCommand())
            {
                delete.Transaction = transaction;
                delete.CommandText = "DELETE FROM source_inputs WHERE url NOT IN ($responses, $coordinates);";
                delete.Parameters.AddWithValue("$responses", settings.Source.Url.AbsoluteUri);
                delete.Parameters.AddWithValue("$coordinates", settings.Source.CoordinatesUrl.AbsoluteUri);
                delete.ExecuteNonQuery();
            }

            using (var state = connection.CreateCommand())
            {
                state.Transaction = transaction;
                state.CommandText = """
                    UPDATE dataset_state
                    SET source_url = $sourceUrl,
                        version = $version,
                        last_checked_at_unix_seconds = $checkedAt,
                        last_changed_at_unix_seconds = $checkedAt,
                        place_count = $placeCount
                    WHERE id = 1;
                    """;
                state.Parameters.AddWithValue("$sourceUrl", settings.Source.Url.ToString());
                state.Parameters.AddWithValue("$version", version);
                state.Parameters.AddWithValue("$checkedAt", checkedAtUtc.ToUnixTimeSeconds());
                state.Parameters.AddWithValue("$placeCount", places.Count);
                state.ExecuteNonQuery();
            }

            transaction.Commit();
        }
        catch
        {
            transaction.Rollback();
            throw;
        }
    }

    private static void SaveInputs(
        SqliteConnection connection, SqliteTransaction transaction, IReadOnlyList<SourceInputRow> inputs)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO source_inputs (url, payload, etag, last_modified)
            VALUES ($url, $payload, $etag, $lastModified)
            ON CONFLICT (url) DO UPDATE SET
                payload = excluded.payload, etag = excluded.etag, last_modified = excluded.last_modified;
            """;
        var url = command.Parameters.Add("$url", SqliteType.Text);
        var payload = command.Parameters.Add("$payload", SqliteType.Blob);
        var etag = command.Parameters.Add("$etag", SqliteType.Text);
        var lastModified = command.Parameters.Add("$lastModified", SqliteType.Text);
        foreach (var input in inputs)
        {
            url.Value = input.Url.AbsoluteUri;
            payload.Value = input.Payload;
            etag.Value = (object?)input.Etag ?? DBNull.Value;
            lastModified.Value = (object?)input.LastModified ?? DBNull.Value;
            command.ExecuteNonQuery();
        }
    }

    private SqliteConnection OpenConnection()
    {
        var connection = new SqliteConnection(_connectionString);
        connection.Open();

        using var command = connection.CreateCommand();
        command.CommandText = "PRAGMA busy_timeout=5000;";
        command.ExecuteNonQuery();

        return connection;
    }

    private static PlaceDto ReadPlace(SqliteDataReader reader) =>
        new(
            reader.GetString(0),
            reader.GetString(1),
            reader.GetString(2),
            reader.GetDouble(3),
            reader.GetDouble(4),
            reader.GetDouble(5),
            new RatingBreakdownDto(
                reader.GetInt32(7),
                reader.GetInt32(8),
                reader.GetInt32(9),
                reader.GetInt32(6)));
}
