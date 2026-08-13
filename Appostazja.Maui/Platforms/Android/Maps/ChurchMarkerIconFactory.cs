using System.Collections;
using System.Globalization;
using Appostazja.Maui.Models;
using Android.Gms.Maps.Model;
using Android.Gms.Maps.Utils.Clustering;
using Android.Graphics;
using Bitmap = Android.Graphics.Bitmap;
using Canvas = Android.Graphics.Canvas;
using Color = Android.Graphics.Color;
using Paint = Android.Graphics.Paint;
using RectF = Android.Graphics.RectF;

namespace Appostazja.Maui.Platforms.Android.Maps;

internal sealed class ChurchMarkerIconFactory(global::Android.Content.Context context) : IDisposable
{
    private const int MaximumCachedClusterIcons = 128;
    private const float ArcGapDegrees = 2;

    private static readonly Color BadColor = Color.ParseColor("#C62828");
    private static readonly Color AverageColor = Color.ParseColor("#F9A825");
    private static readonly Color GoodColor = Color.ParseColor("#2E7D32");
    private static readonly Color RingTrackColor = Color.ParseColor("#E7DED4");
    private static readonly Color ClusterSurfaceColor = Color.ParseColor("#FFF9F3");
    private static readonly Color ClusterBorderColor = Color.ParseColor("#6B5144");
    private static readonly Color ClusterTextColor = Color.ParseColor("#2D211C");

    private readonly float density = Math.Max(context.Resources?.DisplayMetrics?.Density ?? 1, 1);
    private readonly Dictionary<ClusterIconKey, BitmapDescriptor> clusterIcons =
        new(MaximumCachedClusterIcons);
    private readonly Queue<ClusterIconKey> clusterIconOrder =
        new(MaximumCachedClusterIcons);
    private readonly Paint paint = new(PaintFlags.AntiAlias);
    private readonly RectF arcBounds = new();
    private readonly Typeface boldTypeface = Typeface.Create(Typeface.Default, TypefaceStyle.Bold)!;

    private BitmapDescriptor? badPin;
    private BitmapDescriptor? averagePin;
    private BitmapDescriptor? goodPin;
    private bool disposed;

    public BitmapDescriptor GetPinIcon(ChurchRatingCategory category)
    {
        ObjectDisposedException.ThrowIf(disposed, this);

        return category switch
        {
            ChurchRatingCategory.Bad =>
                badPin ??= BitmapDescriptorFactory.FromResource(Resource.Drawable.pin_bad),
            ChurchRatingCategory.Average =>
                averagePin ??= BitmapDescriptorFactory.FromResource(Resource.Drawable.pin_average),
            _ =>
                goodPin ??= BitmapDescriptorFactory.FromResource(Resource.Drawable.pin_good),
        };
    }

    public BitmapDescriptor GetClusterIcon(ICluster cluster)
    {
        ObjectDisposedException.ThrowIf(disposed, this);

        ClusterComposition composition = CountRatings(cluster);
        var key = new ClusterIconKey(
            composition.Total,
            composition.Bad,
            composition.Average,
            composition.Good);

        if (clusterIcons.TryGetValue(key, out BitmapDescriptor? cached))
        {
            return cached;
        }

        BitmapDescriptor descriptor = CreateClusterIcon(composition);
        AddClusterIconToCache(key, descriptor);

        return descriptor;
    }

    public void Dispose()
    {
        if (disposed)
        {
            return;
        }

        disposed = true;

        badPin?.Dispose();
        averagePin?.Dispose();
        goodPin?.Dispose();

        foreach (BitmapDescriptor descriptor in clusterIcons.Values)
        {
            descriptor.Dispose();
        }

        clusterIcons.Clear();
        clusterIconOrder.Clear();
        arcBounds.Dispose();
        paint.Dispose();
        boldTypeface.Dispose();
    }

    private void AddClusterIconToCache(
        ClusterIconKey key,
        BitmapDescriptor descriptor)
    {
        while (clusterIcons.Count >= MaximumCachedClusterIcons &&
               clusterIconOrder.TryDequeue(out ClusterIconKey oldestKey))
        {
            if (clusterIcons.Remove(oldestKey, out BitmapDescriptor? oldestDescriptor))
            {
                oldestDescriptor.Dispose();
            }
        }

        clusterIcons.Add(key, descriptor);
        clusterIconOrder.Enqueue(key);
    }

    private BitmapDescriptor CreateClusterIcon(ClusterComposition composition)
    {
        int diameter = Dp(composition.Total switch
        {
            < 10 => 42,
            < 100 => 47,
            < 1000 => 52,
            _ => 57,
        });
        float center = diameter / 2f;
        float outerPadding = Dp(2);
        float ringWidth = Dp(4.5f);
        float ringRadius = center - outerPadding - (ringWidth / 2);
        float innerRadius = ringRadius - (ringWidth / 2) - Dp(3);

        using Bitmap bitmap = Bitmap.CreateBitmap(
            diameter,
            diameter,
            Bitmap.Config.Argb8888!);
        using var canvas = new Canvas(bitmap);

        paint.SetStyle(Paint.Style.Fill);
        paint.Color = Color.White;
        canvas.DrawCircle(center, center, ringRadius + (ringWidth / 2) + Dp(1), paint);

        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = ringWidth;
        paint.StrokeCap = Paint.Cap.Butt;
        paint.Color = RingTrackColor;

        arcBounds.Set(
            center - ringRadius,
            center - ringRadius,
            center + ringRadius,
            center + ringRadius);
        canvas.DrawArc(arcBounds, -90, 360, false, paint);
        DrawRatingArcs(canvas, arcBounds, paint, composition);

        paint.SetStyle(Paint.Style.Fill);
        paint.Color = ClusterSurfaceColor;
        canvas.DrawCircle(center, center, innerRadius, paint);

        paint.SetStyle(Paint.Style.Stroke);
        paint.StrokeWidth = Dp(1);
        paint.Color = ClusterBorderColor;
        canvas.DrawCircle(center, center, innerRadius, paint);

        paint.SetStyle(Paint.Style.Fill);
        paint.Color = ClusterTextColor;
        paint.TextAlign = Paint.Align.Center;
        paint.TextSize = Dp(composition.Total < 100 ? 15 : 14);
        paint.SetTypeface(boldTypeface);

        Paint.FontMetrics? metrics = paint.GetFontMetrics();
        float baseline = metrics is null
            ? center
            : center - ((metrics.Ascent + metrics.Descent) / 2);
        canvas.DrawText(
            composition.Total.ToString(CultureInfo.InvariantCulture),
            center,
            baseline,
            paint);

        return BitmapDescriptorFactory.FromBitmap(bitmap);
    }

    private static void DrawRatingArcs(
        Canvas canvas,
        RectF bounds,
        Paint paint,
        ClusterComposition composition)
    {
        int visibleSegments =
            (composition.Bad > 0 ? 1 : 0) +
            (composition.Average > 0 ? 1 : 0) +
            (composition.Good > 0 ? 1 : 0);
        float drawableDegrees = 360 - (visibleSegments * ArcGapDegrees);
        float angle = -90 + (ArcGapDegrees / 2);

        angle = DrawRatingArc(
            canvas, bounds, paint, composition.Bad, composition.Total,
            drawableDegrees, angle, BadColor);
        angle = DrawRatingArc(
            canvas, bounds, paint, composition.Average, composition.Total,
            drawableDegrees, angle, AverageColor);
        DrawRatingArc(
            canvas, bounds, paint, composition.Good, composition.Total,
            drawableDegrees, angle, GoodColor);
    }

    private static float DrawRatingArc(
        Canvas canvas,
        RectF bounds,
        Paint paint,
        int count,
        int total,
        float drawableDegrees,
        float angle,
        Color color)
    {
        if (count <= 0)
        {
            return angle;
        }

        float sweep = drawableDegrees * count / total;
        paint.Color = color;
        canvas.DrawArc(bounds, angle, sweep, false, paint);
        return angle + sweep + ArcGapDegrees;
    }

    private static ClusterComposition CountRatings(ICluster cluster)
    {
        int bad = 0;
        int total = cluster.Size;
        int average = 0;
        int good = 0;

        if (cluster.Items is IEnumerable items)
        {
            foreach (object? item in items)
            {
                if (item is not ChurchClusterItem church)
                {
                    continue;
                }

                switch (church.Marker.RatingCategory)
                {
                    case ChurchRatingCategory.Bad:
                        bad++;
                        break;
                    case ChurchRatingCategory.Average:
                        average++;
                        break;
                    case ChurchRatingCategory.Good:
                        good++;
                        break;
                }
            }
        }

        int classified = bad + average + good;
        average += Math.Max(total - classified, 0);

        return new ClusterComposition(total, bad, average, good);
    }

    private int Dp(float value) =>
        Math.Max((int)MathF.Round(value * density), 1);

    private readonly record struct ClusterComposition(
        int Total,
        int Bad,
        int Average,
        int Good);

    private readonly record struct ClusterIconKey(
        int Total,
        int Bad,
        int Average,
        int Good);
}
