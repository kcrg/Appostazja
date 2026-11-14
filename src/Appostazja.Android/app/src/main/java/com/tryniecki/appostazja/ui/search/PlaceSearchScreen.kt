package com.tryniecki.appostazja.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.foundation.layout.heightIn
import com.tryniecki.appostazja.ui.components.EmptyState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.api.model.PlaceDto
import com.tryniecki.appostazja.ui.formatRating
import com.tryniecki.appostazja.ui.theme.RatingBad
import com.tryniecki.appostazja.ui.theme.RatingGood
import com.tryniecki.appostazja.ui.theme.RatingNeutral
import com.composables.icons.tabler.outline.R as TablerIcons

@Composable
internal fun PlaceSearchScreen(
    state: PlaceSearchUiState,
    onQueryChange: (String) -> Unit,
    onSortChange: (SearchSort) -> Unit,
    onWithReportsOnlyChange: (Boolean) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onPlaceClick: (PlaceDto) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Ask for the next page a few rows before the end of the list.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { nearEnd }.collect { if (it) onLoadMore() }
    }
    // A new search starts at the top of its results.
    LaunchedEffect(state.query, state.sort, state.withReportsOnly) { listState.scrollToItem(0) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        painterResource(TablerIcons.drawable.tabler_ic_arrow_left_outline),
                        contentDescription = stringResource(R.string.search_close),
                    )
                }
                TextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SearchBarDefaults.InputFieldHeight)
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    singleLine = true,
                    shape = SearchBarDefaults.inputFieldShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    leadingIcon = {
                        Icon(painterResource(TablerIcons.drawable.tabler_ic_search_outline), contentDescription = null)
                    },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    painterResource(TablerIcons.drawable.tabler_ic_x_outline),
                                    contentDescription = stringResource(R.string.search_clear),
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchSort.entries.forEach { sort ->
                    FilterChip(
                        selected = state.sort == sort,
                        onClick = { onSortChange(sort) },
                        label = { Text(stringResource(sort.label)) },
                    )
                }
                FilterChip(
                    selected = state.withReportsOnly,
                    onClick = { onWithReportsOnlyChange(!state.withReportsOnly) },
                    label = { Text(stringResource(R.string.search_with_reports_only)) },
                )
            }

            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            when {
                state.error && state.results.isEmpty() -> EmptyState(
                    icon = TablerIcons.drawable.tabler_ic_wifi_off_outline,
                    title = stringResource(R.string.search_error),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onRetry,
                )
                !state.isLoading && state.results.isEmpty() -> EmptyState(
                    icon = TablerIcons.drawable.tabler_ic_search_off_outline,
                    title = stringResource(R.string.search_empty),
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "count", contentType = "count") {
                        Text(
                            pluralStringResource(R.plurals.search_results_count, state.total, state.total),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    items(state.results, key = { it.id }, contentType = { "place" }) { place ->
                        PlaceRow(place, onClick = { onPlaceClick(place) })
                        HorizontalDivider(
                            Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    if (state.isLoadingMore) {
                        item(key = "more", contentType = "more") {
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp))
                        }
                    } else if (state.error) {
                        item(key = "retry", contentType = "retry") {
                            TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: PlaceDto, onClick: () -> Unit) {
    val locale = LocalLocale.current.platformLocale
    ListItem(
        onClick = onClick,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        supportingContent = { Text(place.address, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                place.rating?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(toneColor(place.tone), CircleShape))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            stringResource(R.string.place_rating, formatRating(rating, locale)),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Text(
                    pluralStringResource(R.plurals.place_reports_count, place.ratings.total, place.ratings.total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Text(place.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun toneColor(tone: String?): Color = when (tone) {
    "positive" -> RatingGood
    "negative" -> RatingBad
    else -> RatingNeutral
}

