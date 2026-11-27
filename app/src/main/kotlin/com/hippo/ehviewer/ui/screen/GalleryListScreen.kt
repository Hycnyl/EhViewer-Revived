package com.hippo.ehviewer.ui.screen

import android.content.Context
import android.view.ViewConfiguration
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.FAB_ANIMATE_TIME
import com.ehviewer.core.ui.component.FabLayout
import com.ehviewer.core.ui.component.IconTooltip
import com.ehviewer.core.ui.component.LocalSideSheetState
import com.ehviewer.core.ui.component.ProvideSideSheetContent
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.filled.GoTo
import com.ehviewer.core.ui.util.asyncState
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.launchIO
import com.ehviewer.core.util.onEachLatest
import com.ehviewer.core.util.withUIContext
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.asMutableState
import com.hippo.ehviewer.client.EhTagDatabase
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_NORMAL
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_SUBSCRIPTION
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_TAG
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_TOPLIST
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_UPLOADER
import com.hippo.ehviewer.client.data.ListUrlBuilder.Companion.MODE_WHATS_HOT
import com.hippo.ehviewer.client.parser.GalleryDetailUrlParser
import com.hippo.ehviewer.client.parser.GalleryPageUrlParser
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.search.CombinedSearchSource
import com.hippo.ehviewer.search.toListUrlBuilder
import com.hippo.ehviewer.ui.DrawerHandle
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.awaitSelectDate
import com.hippo.ehviewer.ui.destinations.ProgressScreenDestination
import com.hippo.ehviewer.ui.doGalleryInfoAction
import com.hippo.ehviewer.ui.main.AdvancedSearchOption
import com.hippo.ehviewer.ui.main.AvatarIcon
import com.hippo.ehviewer.ui.main.GalleryInfoGridItem
import com.hippo.ehviewer.ui.main.GalleryInfoListItem
import com.hippo.ehviewer.ui.main.GalleryList
import com.hippo.ehviewer.ui.main.GalleryViewToggle
import com.hippo.ehviewer.ui.main.SearchFilter
import com.hippo.ehviewer.ui.tools.awaitInputText
import com.hippo.ehviewer.ui.tools.rememberMutableStateInDataStore
import com.hippo.ehviewer.util.FavouriteStatusRouter
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.Direction
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import moe.tarsin.navigate
import moe.tarsin.string

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.HomePageScreen(navigator: DestinationsNavigator) = GalleryListScreen(ListUrlBuilder(), navigator)

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.SubscriptionScreen(navigator: DestinationsNavigator) = GalleryListScreen(ListUrlBuilder(MODE_SUBSCRIPTION), navigator)

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.WhatshotScreen(navigator: DestinationsNavigator) = GalleryListScreen(ListUrlBuilder(MODE_WHATS_HOT), navigator)

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.ToplistScreen(navigator: DestinationsNavigator) = GalleryListScreen(ListUrlBuilder(MODE_TOPLIST, mKeyword = Settings.recentToplist), navigator)

@Destination<RootGraph>
private val combinedSearchDraftSaver = Saver<CombinedSearchSource?, String>(
    save = { it?.let { source -> Json.encodeToString(source) } },
    restore = { Json.decodeFromString<CombinedSearchSource>(it) },
)

@Composable
fun AnimatedVisibilityScope.GalleryListScreen(
    lub: ListUrlBuilder,
    navigator: DestinationsNavigator,
    viewModel: GalleryListViewModel = viewModel { GalleryListViewModel(lub, createSavedStateHandle()) },
) = Screen(navigator) {
    val searchFieldState = rememberTextFieldState()
    var urlBuilder by viewModel.urlBuilder
    var searchBarExpanded by rememberSaveable { mutableStateOf(false) }
    var combinedDraft by rememberSaveable(stateSaver = combinedSearchDraftSaver) { mutableStateOf<CombinedSearchSource?>(null) }
    val searchFocus = LocalFocusManager.current
    var searchBarOffsetY by remember { mutableIntStateOf(0) }
    var fabExpanded by remember { mutableStateOf(false) }
    var fabHidden by remember { mutableStateOf(false) }

    val animateItems by Settings.animateItems.collectAsState()

    var category by rememberMutableStateInDataStore("SearchCategory") { EhUtils.ALL_CATEGORY }
    var advancedSearchOption by rememberMutableStateInDataStore("AdvancedSearchOption") { AdvancedSearchOption() }

    DrawerHandle(!searchBarExpanded)

    LaunchedEffect(urlBuilder) {
        if (urlBuilder.category != EhUtils.NONE) category = urlBuilder.category
        var keyword = urlBuilder.keyword.takeUnless { urlBuilder.mode == MODE_TOPLIST }.orEmpty()
        if (urlBuilder.mode == MODE_TAG) {
            keyword = wrapTagKeyword(keyword)
        }
        searchFieldState.setTextAndPlaceCursorAtEnd(keyword)
    }

    val density = LocalDensity.current
    val positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold
    val listState = rememberLazyGridState()
    val gridState = rememberLazyStaggeredGridState()
    val isTopList = remember(urlBuilder) { urlBuilder.mode == MODE_TOPLIST }
    val ehHint = stringResource(R.string.gallery_list_search_bar_hint_e_hentai)
    val exHint = stringResource(R.string.gallery_list_search_bar_hint_exhentai)
    val searchBarHint by rememberUpdatedState(if (EhUtils.isExHentai) exHint else ehHint)
    val suitableTitle = getSuitableTitleForUrlBuilder(urlBuilder)
    val data = viewModel.data.collectAsLazyPagingItems()
    ReportDrawnWhen { data.loadState.refresh !is LoadState.Loading }
    FavouriteStatusRouter.Observe(data)
    var listMode by Settings.listMode.asMutableState()

    val entries = stringArrayResource(id = com.hippo.ehviewer.R.array.toplist_entries)
    val values = stringArrayResource(id = com.hippo.ehviewer.R.array.toplist_values)
    val toplists = remember { entries zip values }
    var languageFilter by Settings.languageFilter.asMutableState()

    fun getFirstVisibleItemIndex() = if (listMode == 0) {
        listState.firstVisibleItemIndex
    } else {
        gridState.firstVisibleItemIndex
    }

    fun firstVisibleGid(): Long? {
        val index = getFirstVisibleItemIndex()
        return if (index in 0 until data.itemCount) data.peek(index)?.gid else null
    }

    fun combinedSearchSource(): CombinedSearchSource {
        val current = urlBuilder.copy()
        val filters = if (searchBarExpanded) {
            ListUrlBuilder(
                mode = if (current.mode == MODE_SUBSCRIPTION) MODE_SUBSCRIPTION else MODE_NORMAL,
                category = category,
                mKeyword = searchFieldState.text.toString(),
                language = languageFilter,
                advanceSearch = advancedSearchOption.advanceSearch,
                minRating = advancedSearchOption.minRating,
                pageFrom = advancedSearchOption.fromPage,
                pageTo = advancedSearchOption.toPage,
            )
        } else {
            current.copy()
        }
        return CombinedSearchSource(filters, current, firstVisibleGid())
    }

    fun openBuilder(source: CombinedSearchSource) {
        searchFocus.clearFocus()
        combinedDraft = source
    }

    fun finishBuilder() {
        combinedDraft = null
        searchBarExpanded = false
        fabHidden = false
    }

    val combinedTarget by combinedSearchFocus.collectAsState()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val sideSheet = LocalSideSheetState.current
    LaunchedEffect(combinedTarget, lifecycle) {
        if (combinedTarget != null && lifecycle == Lifecycle.State.RESUMED) {
            if (isTopList) {
                navigate(ListUrlBuilder().asDst())
            } else {
                sideSheet.open()
            }
        }
    }

    if (isTopList) {
        ProvideSideSheetContent { sheetState ->
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.toplist)) },
                windowInsets = WindowInsets(),
                colors = topBarOnDrawerColor(),
            )
            toplists.forEach { (name, keyword) ->
                ListItem(
                    modifier = Modifier.padding(horizontal = 4.dp).clip(CardDefaults.shape).clickable {
                        Settings.recentToplist = keyword
                        urlBuilder = ListUrlBuilder(MODE_TOPLIST, mKeyword = keyword)
                        data.refresh()
                        fabHidden = false
                        launch { sheetState.close() }
                    },
                    headlineContent = {
                        Text(text = name)
                    },
                    colors = listItemOnDrawerColor(urlBuilder.keyword == keyword),
                )
            }
        }
    } else {
        ProvideSideSheetContent { sheetState ->
            CombinedSearchSheet(
                drawerOpen = sheetState.isOpen,
                current = ::combinedSearchSource,
                onOpenBuilder = { source ->
                    launch {
                        sheetState.close()
                        openBuilder(source)
                    }
                },
                editingSource = { item ->
                    CombinedSearchSource(
                        filters = item.toListUrlBuilder(languageFilter),
                        currentResult = urlBuilder.copy(),
                        firstVisibleGid = firstVisibleGid(),
                        savedId = item.id,
                    )
                },
                onRun = { item ->
                    val builder = item.toListUrlBuilder(languageFilter)
                    if (urlBuilder.mode == MODE_WHATS_HOT) {
                        navigate(builder.asDst())
                    } else {
                        urlBuilder = builder
                        data.refresh()
                        fabHidden = false
                    }
                    launch { sheetState.close() }
                },
            )
        }
    }

    val openGalleryKeyword = stringResource(R.string.gallery_list_search_bar_open_gallery)
    abstract class UrlSuggestion : Suggestion() {
        override val keyword = openGalleryKeyword
        override val canOpenDirectly = true
        override fun onClick() = navigate(destination)
        abstract val destination: Direction
    }

    class GalleryDetailUrlSuggestion(gid: Long, token: String) : UrlSuggestion() {
        override val destination = gid asDstWith token
    }

    class GalleryPageUrlSuggestion(gid: Long, pToken: String, page: Int) : UrlSuggestion() {
        override val destination = ProgressScreenDestination(gid, pToken, page)
    }

    fun onApplySearch(query: String) = launchIO {
        val builder = ListUrlBuilder()
        val oldMode = urlBuilder.mode
        // If it's MODE_SUBSCRIPTION, keep it
        val newMode = if (oldMode == MODE_SUBSCRIPTION) MODE_SUBSCRIPTION else MODE_NORMAL
        builder.mode = newMode
        builder.keyword = query
        builder.category = category
        builder.language = languageFilter
        builder.advanceSearch = advancedSearchOption.advanceSearch
        builder.minRating = advancedSearchOption.minRating
        builder.pageFrom = advancedSearchOption.fromPage
        builder.pageTo = advancedSearchOption.toPage
        when (oldMode) {
            MODE_TOPLIST, MODE_WHATS_HOT -> {
                // Wait for search view to hide
                delay(300)
                withUIContext { navigate(builder.asDst()) }
            }
            else -> {
                urlBuilder = builder
                data.refresh()
            }
        }
    }

    combinedDraft?.let { source ->
        CombinedSearchBuilderDialog(
            source,
            onDismiss = { combinedDraft = null },
            onRun = { builder ->
                finishBuilder()
                navigate(builder.asDst())
            },
            onSaved = { id ->
                finishBuilder()
                combinedSearchFocus.value = id
            },
        )
    }

    SearchBarScreen(
        onApplySearch = ::onApplySearch,
        expanded = searchBarExpanded,
        onExpandedChange = {
            searchBarExpanded = it
            fabHidden = it
        },
        title = suitableTitle,
        searchFieldHint = searchBarHint,
        searchFieldState = searchFieldState,
        suggestionProvider = {
            GalleryDetailUrlParser.parse(it, false)?.run {
                listOf(GalleryDetailUrlSuggestion(gid, token))
            } ?: GalleryPageUrlParser.parse(it, false)?.run {
                listOf(GalleryPageUrlSuggestion(gid, pToken, page))
            }.orEmpty()
        },
        localSearch = false,
        searchActions = {
            val label = stringResource(R.string.combined_builder)
            IconTooltip(label) {
                IconButton(
                    onClick = { openBuilder(combinedSearchSource()) },
                    shapes = IconButtonDefaults.shapes(),
                ) { Icon(Icons.Default.Tune, contentDescription = label) }
            }
        },
        searchBarOffsetY = { searchBarOffsetY },
        trailingIcon = {
            GalleryViewToggle(listMode != 0, onToggle = { listMode = if (it) 1 else 0 })
            val sheetState = LocalSideSheetState.current
            IconTooltip(
                stringResource(
                    id = if (isTopList) {
                        R.string.toplist
                    } else {
                        R.string.combined_search
                    },
                ),
            ) {
                IconButton(onClick = { launch { sheetState.open() } }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        imageVector = if (isTopList) {
                            Icons.Default.FormatListNumbered
                        } else {
                            Icons.Outlined.Bookmarks
                        },
                        contentDescription = stringResource(
                            id = if (isTopList) {
                                R.string.toplist
                            } else {
                                R.string.combined_search
                            },
                        ),
                    )
                }
            }
            AvatarIcon()
        },
        filter = {
            SearchFilter(
                category = category,
                onCategoryChange = { category = it },
                language = languageFilter,
                onLanguageChange = { languageFilter = it },
                advancedOption = advancedSearchOption,
                onAdvancedOptionChange = { advancedSearchOption = it },
            )
        },
    ) { contentPadding ->
        val height by collectListThumbSizeAsState()
        val showPages by Settings.showGalleryPages.collectAsState()
        val showProgress by Settings.showReadingProgress.collectAsState()
        val searchBarConnection = remember {
            val slop = ViewConfiguration.get(contextOf<Context>()).scaledTouchSlop
            val topPaddingPx = with(density) { contentPadding.calculateTopPadding().roundToPx() }
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val dy = -consumed.y
                    if (dy >= slop) {
                        fabHidden = true
                    } else if (dy <= -slop / 2) {
                        fabHidden = false
                    }
                    searchBarOffsetY = (searchBarOffsetY - dy).roundToInt().coerceIn(-topPaddingPx, 0)
                    return Offset.Zero // We never consume it
                }
            }
        }
        GalleryList(
            data = data,
            contentModifier = Modifier.nestedScroll(searchBarConnection),
            contentPadding = contentPadding,
            listMode = listMode,
            detailListState = listState,
            detailItemContent = { info ->
                GalleryInfoListItem(
                    onClick = { navigate(info.asDst()) },
                    onLongClick = { launch { doGalleryInfoAction(info) } },
                    info = info,
                    showPages = showPages,
                    showProgress = showProgress,
                    modifier = Modifier.height(height),
                )
            },
            thumbListState = gridState,
            thumbItemContent = { info ->
                GalleryInfoGridItem(
                    onClick = { navigate(info.asDst()) },
                    onLongClick = { launch { doGalleryInfoAction(info) } },
                    info = info,
                    showPages = showPages,
                    showProgress = showProgress,
                )
            },
            searchBarOffsetY = { searchBarOffsetY },
            onRefresh = {
                urlBuilder.setRange(0)
                data.refresh()
            },
            onLoading = { searchBarOffsetY = 0 },
        )
    }

    val gotoTitle = stringResource(R.string.go_to)
    val invalidNum = stringResource(R.string.error_invalid_number)
    val outOfRange = stringResource(R.string.error_out_of_range)

    val hideFab by asyncState(
        produce = { fabHidden },
        transform = {
            onEachLatest { hide ->
                if (!hide) delay(FAB_ANIMATE_TIME.toLong())
            }
        },
    )

    FabLayout(
        expandLabel = stringResource(R.string.tooltip_more_actions),
        collapseLabel = stringResource(R.string.tooltip_close_actions),
        hidden = hideFab,
        expanded = fabExpanded,
        onExpandChanged = { fabExpanded = it },
        autoCancel = true,
    ) {
        if (urlBuilder.mode in arrayOf(MODE_NORMAL, MODE_UPLOADER, MODE_TAG)) {
            onClick(Icons.Default.Shuffle, string(R.string.tooltip_random)) {
                urlBuilder.setRange(Random.nextInt(100))
                data.refresh()
            }
        }
        onClick(Icons.Default.Refresh, string(R.string.tooltip_refresh)) {
            urlBuilder.setRange(0)
            data.refresh()
        }
        if (urlBuilder.mode != MODE_WHATS_HOT) {
            onClick(EhIcons.Default.GoTo, string(R.string.go_to)) {
                if (isTopList) {
                    val hint = string(R.string.go_to_hint, urlBuilder.page, TOPLIST_PAGES)
                    val text = awaitInputText(title = gotoTitle, hint = hint, isNumber = true) { oriText ->
                        val goto = ensureNotNull(oriText.trim().toIntOrNull()) { invalidNum }
                        ensure(goto in 1..TOPLIST_PAGES) { outOfRange }
                    }
                    urlBuilder.page = text.trim().toInt()
                } else {
                    val date = awaitSelectDate()
                    urlBuilder.setSeek(date)
                }
                data.refresh()
            }
            onClick(Icons.AutoMirrored.Default.LastPage, string(R.string.tooltip_last_page)) {
                if (isTopList) {
                    urlBuilder.page = TOPLIST_PAGES
                } else {
                    urlBuilder.setIndex("1", false)
                }
                data.refresh()
            }
        }
    }
}

const val TOPLIST_PAGES = 200

@Composable
@Stable
context(_: Context)
private fun getSuitableTitleForUrlBuilder(urlBuilder: ListUrlBuilder, appName: Boolean = true): String? {
    val keyword = urlBuilder.keyword
    val category = urlBuilder.category
    val mode = urlBuilder.mode
    return if (mode == MODE_WHATS_HOT) {
        stringResource(R.string.whats_hot)
    } else if (!keyword.isNullOrEmpty()) {
        when (mode) {
            MODE_TOPLIST -> {
                when (keyword) {
                    "11" -> stringResource(R.string.toplist_alltime)
                    "12" -> stringResource(R.string.toplist_pastyear)
                    "13" -> stringResource(R.string.toplist_pastmonth)
                    "15" -> stringResource(R.string.toplist_yesterday)
                    else -> null
                }
            }
            MODE_TAG -> {
                val canTranslate = Settings.showTagTranslations.value && EhTagDatabase.translatable && EhTagDatabase.initialized
                wrapTagKeyword(keyword, canTranslate)
            }
            else -> keyword
        }
    } else if (category == EhUtils.NONE && urlBuilder.advanceSearch == -1) {
        val appNameStr = stringResource(R.string.app_name)
        val homepageStr = stringResource(R.string.homepage)
        when (mode) {
            MODE_NORMAL -> if (appName) appNameStr else homepageStr
            MODE_SUBSCRIPTION -> stringResource(R.string.subscription)
            else -> null
        }
    } else if (category.countOneBits() == 1) {
        EhUtils.getCategory(category)
    } else {
        null
    }
}
