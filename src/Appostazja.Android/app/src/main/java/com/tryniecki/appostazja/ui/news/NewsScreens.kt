package com.tryniecki.appostazja.ui.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.browser.rememberOpenCustomTab
import com.tryniecki.appostazja.data.NewsArticle
import com.tryniecki.appostazja.data.resolveArticleLink
import com.tryniecki.appostazja.ui.components.EmptyState
import com.tryniecki.appostazja.ui.components.ExpressiveLoadingIndicator
import com.tryniecki.appostazja.ui.formatDate
import kotlinx.coroutines.CancellationException
import com.composables.icons.tabler.outline.R as Tabler

/** How many articles the card on the More screen shows. */
private const val CARD_ARTICLES = 2

sealed interface NewsState {
    data object Loading : NewsState
    data class Loaded(val articles: List<NewsArticle>) : NewsState
    data object Failed : NewsState
}

/** Loads the news once, and again whenever [retry] is called. */
@Composable
fun rememberNewsState(load: suspend () -> List<NewsArticle>): Pair<NewsState, () -> Unit> {
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    val state by produceState<NewsState>(NewsState.Loading, attempt) {
        value = NewsState.Loading
        value = try {
            NewsState.Loaded(load())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            NewsState.Failed
        }
    }
    return state to { attempt++ }
}

/**
 * The body of the news card on the More screen: the latest articles, then a link to all of them. The articles get a
 * fixed area, so the card keeps its height while loading, after an error and once the articles are in.
 */
@Composable
fun NewsCardContent(
    state: NewsState,
    onRetry: () -> Unit,
    onOpenArticle: (NewsArticle) -> Unit,
    onOpenAll: () -> Unit,
    divider: @Composable () -> Unit,
    allRow: @Composable (onClick: () -> Unit) -> Unit,
) {
    val teaserHeight = compactTeaserHeight()
    val areaHeight = teaserHeight * CARD_ARTICLES + DividerDefaults.Thickness * (CARD_ARTICLES - 1)
    Box(Modifier.fillMaxWidth().height(areaHeight), contentAlignment = Alignment.Center) {
        when (state) {
            NewsState.Loading -> ExpressiveLoadingIndicator()
            NewsState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.news_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            is NewsState.Loaded -> {
                val latest = state.articles.take(CARD_ARTICLES)
                if (latest.isEmpty()) {
                    Text(
                        stringResource(R.string.news_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        latest.forEachIndexed { index, article ->
                            if (index > 0) divider()
                            ArticleTeaser(
                                article,
                                onClick = { onOpenArticle(article) },
                                modifier = Modifier.height(teaserHeight),
                                titleLines = 1,
                                excerptLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
    divider()
    allRow(onOpenAll)
}

/** A teaser of one line each for the date, the title and the summary. */
@Composable
private fun compactTeaserHeight(): Dp {
    val typography = MaterialTheme.typography
    val lines = with(LocalDensity.current) {
        typography.labelMedium.lineHeight.toDp() + typography.titleMedium.lineHeight.toDp() +
            typography.bodyMedium.lineHeight.toDp()
    }
    return lines + TEASER_LINE_GAP * 2 + TEASER_VERTICAL_PADDING * 2
}

private val TEASER_LINE_GAP = 4.dp
private val TEASER_VERTICAL_PADDING = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsListScreen(
    load: suspend () -> List<NewsArticle>,
    onOpenArticle: (NewsArticle) -> Unit,
    onBack: () -> Unit,
) {
    val (state, retry) = rememberNewsState(load)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.news_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)
        when (state) {
            NewsState.Loading -> Box(modifier, contentAlignment = Alignment.Center) { ExpressiveLoadingIndicator() }
            NewsState.Failed -> Box(modifier, contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Tabler.drawable.tabler_ic_wifi_off_outline,
                    title = stringResource(R.string.news_error),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = retry,
                )
            }
            is NewsState.Loaded -> if (state.articles.isEmpty()) {
                Box(modifier, contentAlignment = Alignment.Center) {
                    EmptyState(icon = Tabler.drawable.tabler_ic_news_outline, title = stringResource(R.string.news_empty))
                }
            } else {
                LazyColumn(
                    modifier = modifier,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.articles, key = { it.id }) { article ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            ArticleTeaser(article, onClick = { onOpenArticle(article) }, excerptLines = 4)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsArticleScreen(
    articleId: String,
    load: suspend (String) -> NewsArticle?,
    onBack: () -> Unit,
) {
    val (state, retry) = rememberNewsState { listOfNotNull(load(articleId)) }
    val article = (state as? NewsState.Loaded)?.articles?.firstOrNull()
    val openCustomTab = rememberOpenCustomTab()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.news_title)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (article != null) {
                        IconButton(onClick = { openCustomTab(article.url) }) {
                            Icon(
                                painterResource(Tabler.drawable.tabler_ic_external_link_outline),
                                contentDescription = stringResource(R.string.news_open_source),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)
        when {
            state == NewsState.Loading -> Box(modifier, contentAlignment = Alignment.Center) { ExpressiveLoadingIndicator() }
            state == NewsState.Failed -> Box(modifier, contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Tabler.drawable.tabler_ic_wifi_off_outline,
                    title = stringResource(R.string.news_error),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = retry,
                )
            }
            article == null -> Box(modifier, contentAlignment = Alignment.Center) {
                EmptyState(icon = Tabler.drawable.tabler_ic_news_outline, title = stringResource(R.string.news_article_missing))
            }
            else -> ArticleBody(article, openCustomTab, modifier)
        }
    }
}

@Composable
private fun ArticleBody(article: NewsArticle, openCustomTab: (String) -> Unit, modifier: Modifier) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val content = remember(article, linkColor) {
        AnnotatedString.fromHtml(
            article.contentHtml.withParagraphGaps(),
            linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            linkInteractionListener = { link ->
                val url = (link as? LinkAnnotation.Url)?.url ?: return@fromHtml
                val target = resolveArticleLink(article, url)
                when {
                    target.startsWith("https://") || target.startsWith("http://") -> openCustomTab(target)
                    target.startsWith("mailto:") -> runCatching { uriHandler.openUri(target) }
                }
            },
        ).trimmed()
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        Text(article.title, style = MaterialTheme.typography.headlineSmall)
        article.published?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                formatDate(it, LocalLocale.current.platformLocale),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(content, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = { openCustomTab(article.url) }) {
            Icon(
                painterResource(Tabler.drawable.tabler_ic_external_link_outline),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.news_open_source))
        }
    }
}

/** Title, date and the start of the summary as plain text. */
@Composable
private fun ArticleTeaser(
    article: NewsArticle,
    onClick: () -> Unit,
    excerptLines: Int,
    modifier: Modifier = Modifier,
    titleLines: Int = Int.MAX_VALUE,
) {
    val excerpt = remember(article.summaryHtml) { AnnotatedString.fromHtml(article.summaryHtml).text.trim() }
    val locale = LocalLocale.current.platformLocale
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = TEASER_VERTICAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(TEASER_LINE_GAP),
    ) {
        article.published?.let {
            Text(
                formatDate(it, locale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(article.title, style = MaterialTheme.typography.titleMedium, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
        if (excerpt.isNotEmpty()) {
            Text(
                excerpt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = excerptLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            painterResource(Tabler.drawable.tabler_ic_arrow_left_outline),
            contentDescription = stringResource(R.string.action_back),
        )
    }
}

/** Paragraph breaks leave blank lines at the end of the converted HTML. */
private fun AnnotatedString.trimmed(): AnnotatedString {
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    val start = text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
    return if (start >= end) AnnotatedString("") else subSequence(start, end)
}

/** Compose ends a paragraph with a single line break; a blank line between them reads like the website. */
private fun String.withParagraphGaps(): String = replace(PARAGRAPH_BREAK, "</p><br><p")

private val PARAGRAPH_BREAK = Regex("""</p>\s*<p""", RegexOption.IGNORE_CASE)
