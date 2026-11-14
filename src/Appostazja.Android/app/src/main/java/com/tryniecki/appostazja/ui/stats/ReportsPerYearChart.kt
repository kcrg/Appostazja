package com.tryniecki.appostazja.ui.stats

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.api.model.PeriodStatsDto
import com.tryniecki.appostazja.ui.theme.RatingBad
import com.tryniecki.appostazja.ui.theme.RatingGood
import com.tryniecki.appostazja.ui.theme.RatingNeutral

/**
 * Reports per action year, stacked by rating. Tapping a column shows its figures; the axis carries the rest,
 * so no column is labelled.
 */
@Composable
internal fun ReportsPerYearChart(years: List<PeriodStatsDto>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(years) {
        modelProducer.runTransaction {
            columnModel {
                series(years.map { it.ratings.positive })
                series(years.map { it.ratings.neutral })
                series(years.map { it.ratings.negative })
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val surface = colors.surfaceContainer
    val upTo = stringResource(R.string.stats_years_up_to)
    val periodLabel: (Int) -> String = { index ->
        years.getOrNull(index)?.let { if (it.fromYear == it.toYear) "${it.fromYear}" else "$upTo ${it.toYear}" } ?: ""
    }
    val easy = stringResource(R.string.church_positive)
    val medium = stringResource(R.string.church_neutral)
    val hard = stringResource(R.string.church_negative)
    val axisText = TextStyle(color = colors.onSurfaceVariant, fontSize = MaterialTheme.typography.labelSmall.fontSize)

    // 2 dp of surface colour between stacked segments; only the top segment gets the rounded data end.
    val segmentGap = Fill(surface)
    val columns = listOf(
        rememberLineComponent(Fill(RatingGood), COLUMN_WIDTH, RectangleShape, strokeFill = segmentGap, strokeThickness = 1.dp),
        rememberLineComponent(Fill(RatingNeutral), COLUMN_WIDTH, RectangleShape, strokeFill = segmentGap, strokeThickness = 1.dp),
        rememberLineComponent(
            Fill(RatingBad),
            COLUMN_WIDTH,
            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
            strokeFill = segmentGap,
            strokeThickness = 1.dp,
        ),
    )

    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            style = TextStyle(color = colors.onSurface, fontSize = MaterialTheme.typography.labelMedium.fontSize),
            lineCount = 2,
            padding = Insets(10.dp, 6.dp, 10.dp, 6.dp),
            background = rememberShapeComponent(Fill(colors.surfaceContainerHighest), RoundedCornerShape(8.dp)),
        ),
        valueFormatter = remember(years, easy, medium, hard) {
            DefaultCartesianMarker.ValueFormatter { _, targets ->
                val index = targets.firstOrNull()?.x?.toInt() ?: return@ValueFormatter ""
                val ratings = years.getOrNull(index)?.ratings ?: return@ValueFormatter ""
                "${periodLabel(index)}\n$easy ${ratings.positive} · $medium ${ratings.neutral} · $hard ${ratings.negative}"
            }
        },
        guideline = rememberAxisGuidelineComponent(fill = Fill(colors.outline), shape = RectangleShape),
    )

    val description = years.joinToString { "${periodLabel(years.indexOf(it))}: ${it.ratings.total}" }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(columns),
                columnCollectionSpacing = 10.dp,
                mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
            ),
            startAxis = VerticalAxis.rememberStart(
                line = null,
                label = rememberAxisLabelComponent(style = axisText),
                tick = null,
                // Recessive, solid hairline grid.
                guideline = rememberAxisGuidelineComponent(
                    fill = Fill(colors.outlineVariant),
                    thickness = 1.dp,
                    shape = RectangleShape,
                ),
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = rememberAxisLineComponent(fill = Fill(colors.outlineVariant)),
                label = rememberAxisLabelComponent(style = axisText),
                tick = null,
                guideline = null,
                valueFormatter = remember(years, upTo) {
                    CartesianValueFormatter { _, value, _ -> periodLabel(value.toInt()) }
                },
            ),
            marker = marker,
            markerController = CartesianMarkerController.rememberToggleOnTap(),
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    )
}

private val COLUMN_WIDTH = 18.dp
