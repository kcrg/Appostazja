package com.tryniecki.appostazja.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.tryniecki.appostazja.ui.components.EmptyState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.api.model.CityStatsDto
import com.tryniecki.appostazja.api.model.RatingBreakdownDto
import com.tryniecki.appostazja.api.model.StatsDto
import com.tryniecki.appostazja.data.MapRepository
import com.tryniecki.appostazja.ui.components.ExpressiveLoadingIndicator
import com.tryniecki.appostazja.ui.formatRating
import com.tryniecki.appostazja.ui.theme.RatingBad
import com.tryniecki.appostazja.ui.theme.RatingGood
import com.tryniecki.appostazja.ui.theme.RatingNeutral
import java.text.NumberFormat
import java.util.Locale

@Composable
fun StatsRoute(repository: MapRepository) {
    val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(state, onRetry = viewModel::load)
}

/** One part of a whole: a rating, a stage or a verdict. */
internal data class Part(val label: String, val value: Int, val color: Color)

@Composable
internal fun ratingParts(ratings: RatingBreakdownDto) = listOf(
    Part(stringResource(R.string.church_positive), ratings.positive, RatingGood),
    Part(stringResource(R.string.church_neutral), ratings.neutral, RatingNeutral),
    Part(stringResource(R.string.church_negative), ratings.negative, RatingBad),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsScreen(state: StatsUiState, onRetry: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        val stats = state.stats
        when {
            stats != null -> StatsContent(stats, padding)
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                ExpressiveLoadingIndicator(Modifier.size(48.dp))
            }
            else -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                EmptyState(
                    icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_chart_bar_off_outline,
                    title = stringResource(R.string.stats_error_title),
                    body = stringResource(R.string.stats_error),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onRetry,
                )
            }
        }
    }
}

@Composable
private fun StatsContent(stats: StatsDto, padding: PaddingValues) {
    val locale = LocalLocale.current.platformLocale
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "tiles") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.stats_places), numbers.format(stats.placeCount), Modifier.weight(1f))
                StatTile(stringResource(R.string.stats_reports), numbers.format(stats.reportCount), Modifier.weight(1f))
                StatTile(
                    stringResource(R.string.stats_with_comment),
                    percent(stats.commentCount, stats.reportCount, locale),
                    Modifier.weight(1f),
                )
            }
        }

        item(key = "ratings") {
            ChartCard(
                title = stringResource(R.string.stats_ratings_title),
                subtitle = stringResource(R.string.stats_ratings_subtitle),
            ) {
                val parts = ratingParts(stats.ratings)
                TallSegmentedBar(parts, locale)
                Legend(parts)
            }
        }

        item(key = "stages") {
            ChartCard(
                title = stringResource(R.string.stats_stages_title),
                subtitle = stringResource(R.string.stats_stages_subtitle),
            ) {
                listOf(
                    R.string.church_report_before to stats.stages.beforeApostasy,
                    R.string.church_report_apostasy to stats.stages.apostasy,
                    R.string.church_report_after to stats.stages.afterApostasy,
                ).forEach { (label, ratings) ->
                    LabeledThinBar(
                        label = stringResource(label),
                        trailing = pluralStringResource(R.plurals.place_reports_count, ratings.total, ratings.total),
                        parts = ratingParts(ratings),
                        fraction = 1f,
                        locale = locale,
                    )
                }
                Legend(ratingParts(stats.ratings))
            }
        }

        if (stats.years.isNotEmpty()) item(key = "years") {
            ChartCard(
                title = stringResource(R.string.stats_years_title),
                subtitle = stringResource(R.string.stats_years_subtitle, numbers.format(stats.datedReportCount)),
            ) {
                ReportsPerYearChart(stats.years)
                Legend(ratingParts(stats.ratings))
            }
        }

        if (stats.topCities.isNotEmpty()) item(key = "cities") {
            ChartCard(
                title = stringResource(R.string.stats_cities_title),
                subtitle = stringResource(R.string.stats_cities_subtitle),
            ) {
                val max = stats.topCities.maxOf { it.ratings.total }.coerceAtLeast(1)
                stats.topCities.forEach { city -> CityRow(city, max, locale) }
                Legend(ratingParts(stats.ratings))
            }
        }

        item(key = "tones") {
            ChartCard(
                title = stringResource(R.string.stats_tones_title),
                subtitle = stringResource(R.string.stats_tones_subtitle),
            ) {
                val parts = listOf(
                    Part(stringResource(R.string.stats_tone_positive), stats.placeTones.positive, RatingGood),
                    Part(stringResource(R.string.stats_tone_mixed), stats.placeTones.mixed, RatingNeutral),
                    Part(stringResource(R.string.stats_tone_negative), stats.placeTones.negative, RatingBad),
                )
                TallSegmentedBar(parts, locale)
                Legend(parts)
            }
        }

        item(key = "source") {
            Text(
                stringResource(R.string.stats_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

/**
 * One whole split into a few parts: each part its own block with its share printed inside when it fits,
 * 2 dp surface gaps between blocks and a legend of names below.
 */
@Composable
private fun TallSegmentedBar(parts: List<Part>, locale: Locale) {
    val visible = parts.filter { it.value > 0 }
    val total = visible.sumOf { it.value }.coerceAtLeast(1)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    val description = parts.joinToString { "${it.label}: ${it.value} (${percent(it.value, total, locale)})" }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val gaps = GAP * (visible.size - 1).coerceAtLeast(0)
        val available = maxWidth - gaps
        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
            visible.forEach { part ->
                val width = available * (part.value.toFloat() / total)
                val label = percent(part.value, total, locale)
                val labelWidth = with(density) { measurer.measure(label, labelStyle).size.width.toDp() }
                Box(
                    Modifier
                        .width(width)
                        .height(28.dp)
                        .background(part.color, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // A block too narrow for its label prints none; the legend and description still carry it.
                    if (width >= labelWidth + 12.dp) {
                        Text(
                            label,
                            style = labelStyle,
                            color = onFill(part.color),
                            maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A row label, a figure beside it and a thin split bar; [fraction] scales the bar against the longest row. */
@Composable
private fun LabeledThinBar(label: String, trailing: String, parts: List<Part>, fraction: Float, locale: Locale) {
    val total = parts.sumOf { it.value }
    val description = "$label, $trailing: " +
        parts.joinToString { "${it.label} ${it.value} (${percent(it.value, total, locale)})" }
    Column(
        Modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ThinSegmentedBar(parts, fraction)
    }
}

@Composable
private fun ThinSegmentedBar(parts: List<Part>, fraction: Float) {
    val visible = parts.filter { it.value > 0 }
    val total = visible.sumOf { it.value }.coerceAtLeast(1)
    BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp)) {
        val barWidth = maxWidth * fraction.coerceIn(0f, 1f)
        val available = barWidth - GAP * (visible.size - 1).coerceAtLeast(0)
        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
            visible.forEach { part ->
                Box(
                    Modifier
                        .width(available * (part.value.toFloat() / total))
                        .height(8.dp)
                        .background(part.color, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun CityRow(city: CityStatsDto, maxReports: Int, locale: Locale) {
    val reports = pluralStringResource(R.plurals.place_reports_count, city.ratings.total, city.ratings.total)
    val trailing = city.rating?.let { "$reports · " + stringResource(R.string.place_rating, formatRating(it, locale)) }
        ?: reports
    LabeledThinBar(
        label = city.city,
        trailing = trailing,
        parts = ratingParts(city.ratings),
        fraction = city.ratings.total.toFloat() / maxReports,
        locale = locale,
    )
}

@Composable
internal fun Legend(parts: List<Part>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEach { part ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(part.color, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(6.dp))
                Text(part.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** White or near-black text on a coloured fill, whichever reads better. */
internal fun onFill(fill: Color): Color = if (fill.luminance() > 0.25f) Color(0xFF1A1A1A) else Color.White

internal fun percent(part: Int, total: Int, locale: Locale): String =
    NumberFormat.getPercentInstance(locale).format(if (total <= 0) 0.0 else part.toDouble() / total)

private val GAP: Dp = 2.dp
