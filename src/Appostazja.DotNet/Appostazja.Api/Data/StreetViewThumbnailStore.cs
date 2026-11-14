using ApostasyMap.Api.Configuration;

namespace ApostasyMap.Api.Data;

/// <summary>A downloaded Street View thumbnail and when it was fetched from Google.</summary>
public sealed record StoredThumbnail(byte[] Bytes, DateTimeOffset FetchedAt);

/// <summary>
/// Street View thumbnails on disk, one file per panorama and heading in
/// <see cref="StreetViewSettings.ThumbnailDirectory"/>. The file's modification time is when it was fetched.
/// </summary>
public sealed class StreetViewThumbnailStore(AppSettings settings)
{
    private const string Extension = ".img";

    private string Directory => settings.StreetView.ThumbnailDirectory;

    public void Initialize() => System.IO.Directory.CreateDirectory(Directory);

    public StoredThumbnail? Get(string key)
    {
        var path = PathFor(key);
        try
        {
            var bytes = File.ReadAllBytes(path);
            return new StoredThumbnail(bytes, new DateTimeOffset(File.GetLastWriteTimeUtc(path), TimeSpan.Zero));
        }
        catch (Exception exception) when (exception is FileNotFoundException or DirectoryNotFoundException)
        {
            return null;
        }
    }

    /// <summary>Written to a temporary file first, so a reader never sees half an image.</summary>
    public void Save(string key, byte[] bytes, DateTimeOffset fetchedAt)
    {
        System.IO.Directory.CreateDirectory(Directory);
        var path = PathFor(key);
        var temporary = $"{path}.{Guid.NewGuid():N}.tmp";
        File.WriteAllBytes(temporary, bytes);
        File.SetLastWriteTimeUtc(temporary, fetchedAt.UtcDateTime);
        File.Move(temporary, path, overwrite: true);
    }

    /// <summary>
    /// Deletes thumbnails fetched before <paramref name="olderThan"/>: ones still in use are fetched again when
    /// they expire, so these belong to panoramas no parish points to any more. Also clears interrupted writes.
    /// </summary>
    public int Prune(DateTimeOffset olderThan)
    {
        if (!System.IO.Directory.Exists(Directory))
        {
            return 0;
        }

        var deleted = 0;
        foreach (var file in new DirectoryInfo(Directory).EnumerateFiles())
        {
            var orphan = file.Extension == Extension
                ? file.LastWriteTimeUtc < olderThan.UtcDateTime
                : file.Extension == ".tmp" && file.LastWriteTimeUtc < DateTime.UtcNow.AddHours(-1);
            if (!orphan)
            {
                continue;
            }

            try
            {
                file.Delete();
                deleted++;
            }
            catch (IOException)
            {
            }
        }

        return deleted;
    }

    private string PathFor(string key) => Path.Combine(Directory, key + Extension);
}
