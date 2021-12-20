/*
 * Copyright (C) 2021, Alashov Berkeli
 * All rights reserved.
 */
package tm.alashow.datmusic.ui.playback

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.insets.ui.Scaffold
import com.google.accompanist.insets.ui.TopAppBar
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.PagerState
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tm.alashow.base.ui.ColorPalettePreference
import tm.alashow.base.ui.ThemeState
import tm.alashow.base.util.extensions.Callback
import tm.alashow.common.compose.LocalPlaybackConnection
import tm.alashow.common.compose.LocalScaffoldState
import tm.alashow.common.compose.rememberFlowWithLifecycle
import tm.alashow.datmusic.domain.CoverImageSize
import tm.alashow.datmusic.domain.entities.Audio
import tm.alashow.datmusic.downloader.audioHeader
import tm.alashow.datmusic.playback.*
import tm.alashow.datmusic.playback.models.PlaybackQueue
import tm.alashow.datmusic.playback.models.QueueTitle.Companion.asQueueTitle
import tm.alashow.datmusic.ui.audios.AudioActionHandler
import tm.alashow.datmusic.ui.audios.AudioDropdownMenu
import tm.alashow.datmusic.ui.audios.AudioItemAction
import tm.alashow.datmusic.ui.audios.AudioRow
import tm.alashow.datmusic.ui.audios.LocalAudioActionHandler
import tm.alashow.datmusic.ui.audios.audioActionHandler
import tm.alashow.datmusic.ui.audios.currentPlayingMenuActionLabels
import tm.alashow.datmusic.ui.library.playlist.addTo.AddToPlaylistMenu
import tm.alashow.datmusic.ui.playback.components.PlaybackArtwork
import tm.alashow.datmusic.ui.playback.components.PlaybackNowPlaying
import tm.alashow.datmusic.ui.playback.components.PlaybackNowPlayingWithControls
import tm.alashow.datmusic.ui.playback.components.PlaybackPager
import tm.alashow.navigation.LocalNavigator
import tm.alashow.navigation.Navigator
import tm.alashow.ui.ADAPTIVE_COLOR_ANIMATION
import tm.alashow.ui.DismissableSnackbarHost
import tm.alashow.ui.adaptiveColor
import tm.alashow.ui.components.IconButton
import tm.alashow.ui.components.MoreVerticalIcon
import tm.alashow.ui.isLargeScreen
import tm.alashow.ui.simpleClickable
import tm.alashow.ui.theme.AppTheme
import tm.alashow.ui.theme.LocalThemeState
import tm.alashow.ui.theme.plainBackgroundColor

private val RemoveFromPlaylist = R.string.playback_queue_removeFromQueue
private val AddQueueToPlaylist = R.string.playback_queue_addQueueToPlaylist
private val SaveQueueAsPlaylist = R.string.playback_queue_saveAsPlaylist

@Composable
fun PlaybackSheet(
    // override local theme color palette because we want simple colors for menus n' stuff
    sheetTheme: ThemeState = LocalThemeState.current.copy(colorPalettePreference = ColorPalettePreference.Black),
    navigator: Navigator = LocalNavigator.current,
) {
    val listState = rememberLazyListState()
    val coroutine = rememberCoroutineScope()

    val scrollToTop: Callback = {
        coroutine.launch {
            listState.animateScrollToItem(0)
        }
    }

    val audioActionHandler = audioActionHandler()
    CompositionLocalProvider(LocalAudioActionHandler provides audioActionHandler) {
        AppTheme(theme = sheetTheme, changeSystemBar = false) {
            PlaybackSheetContent(
                onClose = { navigator.goBack() },
                scrollToTop = scrollToTop,
                listState = listState
            )
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
internal fun PlaybackSheetContent(
    onClose: Callback,
    scrollToTop: Callback,
    listState: LazyListState,
    scaffoldState: ScaffoldState = rememberScaffoldState(snackbarHostState = LocalScaffoldState.current.snackbarHostState),
    playbackConnection: PlaybackConnection = LocalPlaybackConnection.current,
    viewModel: PlaybackSheetViewModel = hiltViewModel(),
) {
    val playbackState by rememberFlowWithLifecycle(playbackConnection.playbackState).collectAsState(NONE_PLAYBACK_STATE)
    val playbackQueue by rememberFlowWithLifecycle(playbackConnection.playbackQueue).collectAsState(PlaybackQueue())
    val nowPlaying by rememberFlowWithLifecycle(playbackConnection.nowPlaying).collectAsState(NONE_PLAYING)

    val adaptiveColor = adaptiveColor(nowPlaying.artwork, initial = MaterialTheme.colors.onBackground)
    val contentColor by animateColorAsState(adaptiveColor.color, ADAPTIVE_COLOR_ANIMATION)

    val pagerState = rememberPagerState(playbackQueue.currentIndex)

    if (playbackState == NONE_PLAYBACK_STATE)
        return

    LaunchedEffect(playbackConnection) {
        playbackConnection.playbackState.collectLatest {
//            if (it.isIdle) onClose()
        }
    }

    Scaffold(
        backgroundColor = Color.Transparent,
        modifier = Modifier.background(adaptiveColor.gradient),
        scaffoldState = scaffoldState,
        snackbarHost = {
            DismissableSnackbarHost(it, modifier = Modifier.navigationBarsPadding())
        },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = rememberInsetsPaddingValues(
                insets = LocalWindowInsets.current.systemBars,
                applyTop = true,
                applyBottom = true,
            ),
        ) {
            item {
                PlaybackSheetTopBar(
                    playbackQueue = playbackQueue,
                    onClose = onClose,
                    onTitleClick = viewModel::navigateToQueueSource,
                    onSaveQueueAsPlaylist = viewModel::saveQueueAsPlaylist
                )
                Spacer(Modifier.height(AppTheme.specs.paddingTiny))
            }

            item {
                PlaybackArtworkPagerWithNowPlayingAndControls(
                    nowPlaying = nowPlaying,
                    playbackState = playbackState,
                    pagerState = pagerState,
                    contentColor = contentColor,
                    onTitleClick = viewModel::onTitleClick,
                    onArtistClick = viewModel::onArtistClick,
                    modifier = Modifier.fillParentMaxHeight(0.8f),
                )
            }

            if (playbackQueue.isValid)
                item {
                    PlaybackAudioInfo(audio = playbackQueue.currentAudio)
                }

            playbackQueue(
                playbackQueue = playbackQueue,
                scrollToTop = scrollToTop,
                playbackConnection = playbackConnection,
            )
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
internal fun PlaybackArtworkPagerWithNowPlayingAndControls(
    nowPlaying: MediaMetadataCompat,
    playbackState: PlaybackStateCompat,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colors.onBackground,
    pagerState: PagerState = rememberPagerState(),
    onTitleClick: Callback = {},
    onArtistClick: Callback = {},
) {
    val isLargeScreen by isLargeScreen()
    ConstraintLayout(modifier = modifier) {
        val (pager, nowPlayingControls) = createRefs()
        PlaybackPager(
            nowPlaying = nowPlaying,
            pagerState = pagerState,
            modifier = Modifier
                .constrainAs(pager) {
                    centerHorizontallyTo(parent)
                    top.linkTo(parent.top)
                    bottom.linkTo(nowPlayingControls.top)
                    height = Dimension.fillToConstraints
                }
        ) { audio, _, pagerMod ->
            val currentArtwork = audio.coverUri(CoverImageSize.LARGE)
            Row(
                horizontalArrangement = if (isLargeScreen) Arrangement.Start else Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                PlaybackArtwork(currentArtwork, contentColor, nowPlaying, pagerMod)
                if (isLargeScreen) {
                    PlaybackNowPlaying(
                        nowPlaying = nowPlaying,
                        onTitleClick = onTitleClick,
                        onArtistClick = onArtistClick,
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.align(Alignment.Bottom)
                    )
                }
            }
        }
        PlaybackNowPlayingWithControls(
            nowPlaying = nowPlaying,
            playbackState = playbackState,
            contentColor = contentColor,
            onTitleClick = onTitleClick,
            onArtistClick = onArtistClick,
            onlyControls = isLargeScreen,
            modifier = Modifier.constrainAs(nowPlayingControls) {
                centerHorizontallyTo(parent)
                bottom.linkTo(parent.bottom)
                height = Dimension.fillToConstraints
            }
        )
    }
}

@Composable
private fun PlaybackSheetTopBar(
    playbackQueue: PlaybackQueue,
    onClose: Callback,
    onTitleClick: Callback,
    onSaveQueueAsPlaylist: Callback,
) {
    TopAppBar(
        elevation = 0.dp,
        backgroundColor = Color.Transparent,
        title = { PlaybackSheetTopBarTitle(playbackQueue, onTitleClick) },
        actions = { PlaybackSheetTopBarActions(playbackQueue, onSaveQueueAsPlaylist) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    rememberVectorPainter(Icons.Default.KeyboardArrowDown),
                    modifier = Modifier.size(AppTheme.specs.iconSize),
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
private fun PlaybackSheetTopBarTitle(
    playbackQueue: PlaybackQueue,
    onTitleClick: Callback,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .offset(x = -8.dp) // idk why this is needed for centering
            .simpleClickable(onClick = onTitleClick)
    ) {
        val context = LocalContext.current
        val queueTitle = playbackQueue.title.asQueueTitle()
        Text(
            text = queueTitle.localizeType(context.resources).uppercase(),
            style = MaterialTheme.typography.overline.copy(fontWeight = FontWeight.Light),
            maxLines = 1,
        )
        Text(
            text = queueTitle.localizeValue(),
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
        )
    }
}

@Composable
private fun PlaybackSheetTopBarActions(
    playbackQueue: PlaybackQueue,
    onSaveQueueAsPlaylist: Callback,
    actionHandler: AudioActionHandler = LocalAudioActionHandler.current,
) {
    val (expanded, setExpanded) = remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
        if (playbackQueue.isValid) {
            val (addToPlaylistVisible, setAddToPlaylistVisible) = remember { mutableStateOf(false) }
            val (addQueueToPlaylistVisible, setAddQueueToPlaylistVisible) = remember { mutableStateOf(false) }

            AddToPlaylistMenu(playbackQueue.currentAudio, addToPlaylistVisible, setAddToPlaylistVisible)
            AddToPlaylistMenu(playbackQueue.audios, addQueueToPlaylistVisible, setAddQueueToPlaylistVisible)

            AudioDropdownMenu(
                expanded = expanded,
                onExpandedChange = setExpanded,
                actionLabels = currentPlayingMenuActionLabels,
                extraActionLabels = listOf(AddQueueToPlaylist, SaveQueueAsPlaylist)
            ) { actionLabel ->
                val audio = playbackQueue.currentAudio
                when (val action = AudioItemAction.from(actionLabel, audio)) {
                    is AudioItemAction.AddToPlaylist -> setAddToPlaylistVisible(true)
                    else -> {
                        action.handleExtraActions(actionHandler) {
                            when (it.actionLabelRes) {
                                AddQueueToPlaylist -> setAddQueueToPlaylistVisible(true)
                                SaveQueueAsPlaylist -> onSaveQueueAsPlaylist()
                            }
                        }
                    }
                }
            }
        } else MoreVerticalIcon()
    }
}

@Composable
private fun PlaybackAudioInfo(audio: Audio) {
    val context = LocalContext.current
    val dlItem = audio.audioDownloadItem
    if (dlItem != null) {
        val audiHeader = dlItem.audioHeader(context)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AppTheme.specs.padding)
        ) {
            Surface(
                color = MaterialTheme.colors.plainBackgroundColor().copy(alpha = 0.1f),
                shape = CircleShape,
            ) {
                Text(
                    audiHeader.info(),
                    style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
private fun LazyListScope.playbackQueue(
    playbackQueue: PlaybackQueue,
    scrollToTop: Callback,
    playbackConnection: PlaybackConnection,
) {
    val lastIndex = playbackQueue.audios.size
    val firstIndex = (playbackQueue.currentIndex + 1).coerceAtMost(lastIndex)
    val queue = playbackQueue.audios.subList(firstIndex, lastIndex)
    itemsIndexed(queue, key = { _, a -> a.primaryKey }) { index, audio ->
        val realPosition = firstIndex + index
        AudioRow(
            audio = audio,
            observeNowPlayingAudio = false,
            imageSize = 40.dp,
            onPlayAudio = {
                playbackConnection.transportControls?.skipToQueueItem(realPosition.toLong())
                scrollToTop()
            },
            extraActionLabels = listOf(RemoveFromPlaylist),
            onExtraAction = { playbackConnection.removeByPosition(realPosition) },
            modifier = Modifier.animateItemPlacement()
        )
    }
}
