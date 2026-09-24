using ApostasyMap.Api.Configuration;
using ApostasyMap.Api.Contracts;
using Microsoft.Data.Sqlite;

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

            CREATE TABLE IF NOT EXISTS dataset_state (
                id INTEGER PRIMARY KEY NOT NULL CHECK (id = 1),
                source_url TEXT NOT NULL,
                version TEXT NULL,
                source_etag TEXT NULL,
                source_last_modified TEXT NULL,
                last_checked_at_unix_seconds INTEGER NOT NULL,
                last_changed_at_unix_seconds INTEGER NOT NULL,
                place_count INTEGER NOT NULL
            );

            INSERT OR IGNORE INTO dataset_state (
                id,
                source_url,
                version,
                source_etag,
                source_last_modified,
                last_checked_at_unix_seconds,
                last_changed_at_unix_seconds,
                place_count
            ) VALUES (1, $sourceUrl, NULL, NULL, NULL, 0, 0, 0);
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
                   source_etag,
                   source_last_modified,
                   last_checked_at_unix_seconds,
                   last_changed_at_unix_seconds,
                   place_count
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
            SourceEtag = reader.IsDBNull(2) ? null : reader.GetString(2),
            SourceLastModified = reader.IsDBNull(3) ? null : reader.GetString(3),
            LastCheckedAtUnixSeconds = reader.GetInt64(4),
            LastChangedAtUnixSeconds = reader.GetInt64(5),
            PlaceCount = reader.GetInt32(6)
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

    public void MarkChecked(
        DateTimeOffset checkedAtUtc,
        string? sourceEtag,
        string? sourceLastModified)
    {
        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            UPDATE dataset_state
            SET source_etag = COALESCE($etag, source_etag),
                source_last_modified = COALESCE($lastModified, source_last_modified),
                last_checked_at_unix_seconds = $checkedAt
            WHERE id = 1;
            """;
        command.Parameters.AddWithValue("$etag", (object?)sourceEtag ?? DBNull.Value);
        command.Parameters.AddWithValue("$lastModified", (object?)sourceLastModified ?? DBNull.Value);
        command.Parameters.AddWithValue("$checkedAt", checkedAtUtc.ToUnixTimeSeconds());
        command.ExecuteNonQuery();
    }

    public void ReplaceDataset(
        IReadOnlyList<PlaceRow> places,
        string version,
        string? sourceEtag,
        string? sourceLastModified,
        DateTimeOffset checkedAtUtc)
    {
        using var connection = OpenConnection();
        using var transaction = connection.BeginTransaction();

        try
        {
            using (var delete = connection.CreateCommand())
            {
                delete.Transaction = transaction;
                delete.CommandText = "DELETE FROM places;";
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

            using (var state = connection.CreateCommand())
            {
                state.Transaction = transaction;
                state.CommandText = """
                    UPDATE dataset_state
                    SET source_url = $sourceUrl,
                        version = $version,
                        source_etag = $etag,
                        source_last_modified = $lastModified,
                        last_checked_at_unix_seconds = $checkedAt,
                        last_changed_at_unix_seconds = $checkedAt,
                        place_count = $placeCount
                    WHERE id = 1;
                    """;
                state.Parameters.AddWithValue("$sourceUrl", settings.Source.Url.ToString());
                state.Parameters.AddWithValue("$version", version);
                state.Parameters.AddWithValue("$etag", (object?)sourceEtag ?? DBNull.Value);
                state.Parameters.AddWithValue("$lastModified", (object?)sourceLastModified ?? DBNull.Value);
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
