package com.tryniecki.appostazja.ui.news

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tryniecki.appostazja.R
import com.tryniecki.appostazja.browser.rememberOpenCustomTab
import com.tryniecki.appostazja.data.NewsArticle
import com.tryniecki.appostazja.data.resolveArticleLink
import com.tryniecki.appostazja.ui.components.EmptyState
import com.tryniecki.appostazja.ui.components.ExpressiveLoadingIndicator
import com.tryniecki.appostazja.ui.formatDate
import kotlinx.coroutines.CancellationException
import java.util.Locale
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
    readIds: Set<String>,
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
                                unread = article.id !in readIds,
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
private val TEASER_HORIZONTAL_PADDING = 16.dp
private val UNREAD_DOT = 10.dp
private val UNREAD_DOT_GAP = 3.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsListScreen(
    load: suspend () -> List<NewsArticle>,
    readIds: Set<String>,
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
                        val unread = article.id !in readIds
                        NotchedCard(dots = if (unread) listOf(teaserDotOffset(article)) else emptyList()) {
                            ArticleTeaser(
                                article,
                                unread = unread,
                                onClick = { onOpenArticle(article) },
                                excerptLines = 4,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NewsArticleScreen(
    articleId: String,
    load: suspend (String) -> NewsArticle?,
    onRead: (String) -> Unit,
    onBack: () -> Unit,
) {
    val (state, retry) = rememberNewsState { listOfNotNull(load(articleId)) }
    val article = (state as? NewsState.Loaded)?.articles?.firstOrNull()
    // Read once it is on screen; a failed load leaves it unread.
    LaunchedEffect(article?.id) { article?.let { onRead(it.id) } }
    val openCustomTab = rememberOpenCustomTab()
    // The title of the article is the page title: shown in full above the text, kept on one line once scrolled.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val locale = LocalLocale.current.platformLocale
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        article?.title ?: stringResource(R.string.news_title),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = article?.published?.let { published -> { Text(formatDate(published, locale)) } },
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

/**
 * Title, date and the start of the summary as plain text. The unread dot sits on the card's edge, outside the
 * teaser, so the card that holds it draws it (see [NotchedCard] and [teaserDotOffset]).
 */
@Composable
private fun ArticleTeaser(
    article: NewsArticle,
    unread: Boolean,
    onClick: () -> Unit,
    excerptLines: Int,
    modifier: Modifier = Modifier,
    titleLines: Int = Int.MAX_VALUE,
) {
    val excerpt = remember(article.summaryHtml) { AnnotatedString.fromHtml(article.summaryHtml).text.trim() }
    val locale = LocalLocale.current.platformLocale
    val unreadLabel = stringResource(R.string.news_unread)
    Box(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { if (unread) stateDescription = unreadLabel },
    ) {
        TeaserText(article, excerpt, locale, titleLines, excerptLines)
    }
}

/** How far below the top of a teaser its unread dot is centred: between the date and the title. */
@Composable
private fun teaserDotOffset(article: NewsArticle): Dp {
    val typography = MaterialTheme.typography
    val line = with(LocalDensity.current) {
        if (article.published != null) typography.labelMedium.lineHeight.toDp() + TEASER_LINE_GAP / 2
        else typography.titleMedium.lineHeight.toDp() / 2
    }
    return TEASER_VERTICAL_PADDING + line
}

/** Where the unread dots go on the news card of the More screen, from the top of the card. */
@Composable
fun newsCardDots(state: NewsState, readIds: Set<String>): List<Dp> {
    if (state !is NewsState.Loaded) return emptyList()
    val teaserHeight = compactTeaserHeight()
    return state.articles.take(CARD_ARTICLES).mapIndexedNotNull { index, article ->
        if (article.id in readIds) null
        else (teaserHeight + DividerDefaults.Thickness) * index + teaserDotOffset(article)
    }
}

/**
 * A card with unread dots on its start edge, half outside it. The card is cut out around each dot, so the dot
 * stands apart from it.
 */
@Composable
fun NotchedCard(
    dots: List<Dp>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val baseShape = CardDefaults.shape
    val shape = remember(dots, baseShape) { if (dots.isEmpty()) baseShape else NotchedShape(baseShape, dots) }
    Box(modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            content = content,
        )
        val dotColor = MaterialTheme.colorScheme.primary
        dots.forEach { y ->
            Box(
                Modifier
                    .offset(x = -UNREAD_DOT / 2, y = y - UNREAD_DOT / 2)
                    .size(UNREAD_DOT)
                    .background(dotColor, CircleShape),
            )
        }
    }
}

/** [base] with a circle cut out of its start edge at each of [notches], wider than the dot by [UNREAD_DOT_GAP]. */
private class NotchedShape(private val base: Shape, private val notches: List<Dp>) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val outline = Path().apply { addOutline(base.createOutline(size, layoutDirection, density)) }
        val radius = with(density) { (UNREAD_DOT / 2 + UNREAD_DOT_GAP).toPx() }
        val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width
        val cuts = Path().apply {
            notches.forEach { y -> addOval(Rect(Offset(x, with(density) { y.toPx() }), radius)) }
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, outline, cuts))
    }
}

@Composable
private fun TeaserText(article: NewsArticle, excerpt: String, locale: Locale, titleLines: Int, excerptLines: Int) {
    Column(
        modifier = Modifier.padding(horizontal = TEASER_HORIZONTAL_PADDING, vertical = TEASER_VERTICAL_PADDING),
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
