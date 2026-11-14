using ApostasyMap.Api.Configuration;
using Microsoft.Data.Sqlite;

namespace ApostasyMap.Api.Data;

/// <summary>
/// A looked-up panorama for a parish, or a confirmed absence (<see cref="PanoId"/> null). Only metadata is
/// kept: Google's policies let panorama IDs be stored, but not the images themselves.
/// </summary>
public sealed record StreetViewRow(
    string PlaceId,
    double Latitude,
    double Longitude,
    string? PanoId,
    double PanoLatitude,
    double PanoLongitude,
    string? Copyright,
    string? Date,
    long CheckedAtUnixSeconds);

public sealed class StreetViewStore(AppSettings settings)
{
    private readonly string _connectionString = new SqliteConnectionStringBuilder
    {
        DataSource = settings.Database.Path,
        Mode = SqliteOpenMode.ReadWriteCreate,
        Pooling = true
    }.ToString();

    public void Initialize()
    {
        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            CREATE TABLE IF NOT EXISTS street_view_panoramas (
                place_id TEXT PRIMARY KEY NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                pano_id TEXT NULL,
                pano_latitude REAL NOT NULL,
                pano_longitude REAL NOT NULL,
                copyright TEXT NULL,
                date TEXT NULL,
                checked_at_unix_seconds INTEGER NOT NULL
            );
            """;
        command.ExecuteNonQuery();
    }

    public StreetViewRow? Get(string placeId)
    {
        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT place_id, latitude, longitude, pano_id, pano_latitude, pano_longitude, copyright, date,
                   checked_at_unix_seconds
            FROM street_view_panoramas WHERE place_id = $placeId;
            """;
        command.Parameters.AddWithValue("$placeId", placeId);
        using var reader = command.ExecuteReader();
        if (!reader.Read())
        {
            return null;
        }

        return new StreetViewRow(
            reader.GetString(0),
            reader.GetDouble(1),
            reader.GetDouble(2),
            reader.IsDBNull(3) ? null : reader.GetString(3),
            reader.GetDouble(4),
            reader.GetDouble(5),
            reader.IsDBNull(6) ? null : reader.GetString(6),
            reader.IsDBNull(7) ? null : reader.GetString(7),
            reader.GetInt64(8));
    }

    public void Save(StreetViewRow row)
    {
        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText = """
            INSERT OR REPLACE INTO street_view_panoramas (
                place_id, latitude, longitude, pano_id, pano_latitude, pano_longitude, copyright, date,
                checked_at_unix_seconds)
            VALUES ($placeId, $latitude, $longitude, $panoId, $panoLatitude, $panoLongitude, $copyright, $date,
                    $checkedAt);
            """;
        command.Parameters.AddWithValue("$placeId", row.PlaceId);
        command.Parameters.AddWithValue("$latitude", row.Latitude);
        command.Parameters.AddWithValue("$longitude", row.Longitude);
        command.Parameters.AddWithValue("$panoId", (object?)row.PanoId ?? DBNull.Value);
        command.Parameters.AddWithValue("$panoLatitude", row.PanoLatitude);
        command.Parameters.AddWithValue("$panoLongitude", row.PanoLongitude);
        command.Parameters.AddWithValue("$copyright", (object?)row.Copyright ?? DBNull.Value);
        command.Parameters.AddWithValue("$date", (object?)row.Date ?? DBNull.Value);
        command.Parameters.AddWithValue("$checkedAt", row.CheckedAtUnixSeconds);
        command.ExecuteNonQuery();
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
}
