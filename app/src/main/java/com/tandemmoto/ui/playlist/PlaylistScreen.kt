package com.tandemmoto.ui.playlist

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tandemmoto.R
import com.tandemmoto.library.ImportSummary
import com.tandemmoto.library.Song
import com.tandemmoto.ui.components.BackTopBar
import com.tandemmoto.ui.components.GroupedRow
import com.tandemmoto.ui.components.PartnerTag
import com.tandemmoto.ui.components.SongSeekBar
import com.tandemmoto.ui.components.StatusChip
import com.tandemmoto.ui.components.Tag
import com.tandemmoto.ui.theme.TandemMotoTheme
import com.tandemmoto.ui.theme.groupItemShape
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun PlaylistRoute(
    onBack: () -> Unit,
    viewModel: PlaylistViewModel = viewModel(factory = PlaylistViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val pickFiles = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris: List<Uri> ->
        viewModel.addFiles(uris.map(Uri::toString))
    }
    val pickFolder = rememberLauncherForActivityResult(OpenDocumentTree()) { uri: Uri? ->
        uri?.let { viewModel.addFolder(it.toString()) }
    }
    LifecycleResumeEffect(Unit) {
        viewModel.onVisible()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        viewModel.summaries.collect { summary ->
            snackbar.showSnackbar(summary.message(context))
        }
    }
    PlaylistScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onAddSongs = { pickFiles.launch(arrayOf("audio/*")) },
        onAddFolder = { pickFolder.launch(null) },
        onCheckFolders = viewModel::checkFolders,
        onRemove = viewModel::remove,
        onMove = viewModel::move,
        onPlay = viewModel::play,
        onPlayPause = viewModel::playPause,
        onSeek = viewModel::seek
    )
}

/** "Added 8 songs · 2 already in the playlist · 1 couldn't be read". */
private fun ImportSummary.message(context: Context): String {
    val res = context.resources
    if (folderRefused) return context.getString(R.string.playlist_folder_refused)
    if (fromFolder && added + alreadyThere + unreadable + overLimit == 0) {
        return context.getString(R.string.playlist_no_songs_in_folder)
    }
    if (added == 0 && alreadyThere == 1 && unreadable + overLimit == 0) {
        return context.getString(R.string.playlist_already_there_one)
    }
    return listOfNotNull(
        res.getQuantityString(R.plurals.playlist_added, added, added),
        alreadyThere.takeIf { it > 0 }
            ?.let { res.getQuantityString(R.plurals.playlist_already_there, it, it) },
        unreadable.takeIf { it > 0 }
            ?.let { res.getQuantityString(R.plurals.playlist_unreadable, it, it) },
        overLimit.takeIf { it > 0 }
            ?.let { res.getQuantityString(R.plurals.playlist_over_limit, it, it) }
    ).joinToString(" · ")
}

@Composable
fun PlaylistScreen(
    state: PlaylistUiState,
    onBack: () -> Unit,
    onAddSongs: () -> Unit,
    onAddFolder: () -> Unit,
    onCheckFolders: () -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onPlay: (Int) -> Unit = {},
    onPlayPause: () -> Unit = {},
    onSeek: (Long) -> Unit = {}
) {
    Scaffold(
        topBar = {
            if (state.rows.isEmpty()) {
                BackTopBar(stringResource(R.string.playlist_title), onBack)
            } else {
                PlaylistTopBar(state, onBack, onAddSongs, onAddFolder, onCheckFolders)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            state.nowPlaying?.let { NowPlayingBarView(it, onPlayPause, onSeek) }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !state.loaded -> Unit
                state.rows.isEmpty() -> EmptyPlaylist(onAddSongs, onAddFolder)
                else -> SongList(state, onRemove, onMove, onPlay)
            }
        }
    }
}

/** The song playing, with play/pause and a seek bar, under the list. */
@Composable
private fun NowPlayingBarView(bar: NowPlayingBar, onPlayPause: () -> Unit, onSeek: (Long) -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bar.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    bar.artist?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                    Icon(
                        painter = painterResource(
                            if (bar.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        ),
                        contentDescription = stringResource(
                            if (bar.isPlaying) R.string.ride_pause else R.string.ride_play
                        ),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            SongSeekBar(
                positionMs = bar.positionMs,
                durationMs = bar.durationMs,
                enabled = true,
                onSeek = onSeek,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun PlaylistTopBar(
    state: PlaylistUiState,
    onBack: () -> Unit,
    onAddSongs: () -> Unit,
    onAddFolder: () -> Unit,
    onCheckFolders: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    BackTopBar(
        title = stringResource(R.string.playlist_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.playlist_add))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.playlist_add_songs))
                            if (state.fileSlotsLeft < FEW_FILE_SLOTS) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.playlist_add_songs_left,
                                        state.fileSlotsLeft,
                                        state.fileSlotsLeft
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    onClick = {
                        menu = false
                        onAddSongs()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_add_folder)) },
                    onClick = {
                        menu = false
                        onAddFolder()
                    }
                )
                if (state.hasFolders) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlist_check_folders)) },
                        onClick = {
                            menu = false
                            onCheckFolders()
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun EmptyPlaylist(onAddSongs: () -> Unit, onAddFolder: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(stringResource(R.string.playlist_empty), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.playlist_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onAddSongs, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.playlist_add_songs))
        }
        OutlinedButton(onClick = onAddFolder, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.playlist_add_folder))
        }
    }
}

/**
 * The songs, reorderable by dragging a row's handle: the row follows the finger and swaps with a
 * neighbour once it's dragged past half of that neighbour; the move is saved on release. Held near
 * the top or bottom edge, the list scrolls by itself, faster closer to the edge, so a song can go
 * from 100th to 1st in one drag (phone test on #48).
 */
@Composable
private fun SongList(
    state: PlaylistUiState,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlay: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val edgeZone = with(LocalDensity.current) { AUTO_SCROLL_EDGE.toPx() }
    val maxStep = with(LocalDensity.current) { AUTO_SCROLL_MAX_STEP.toPx() }
    var order by remember { mutableStateOf(state.rows) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableIntStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }
    var autoScroll by remember { mutableStateOf<Job?>(null) }
    if (dragging == null) order = state.rows
    val songCount = state.songCount

    // A list keeps its scroll tied to the row at the top of the screen, so moving that row (e.g.
    // Move to bottom) scrolled the view along with it (phone test on #48). After a reorder, stay
    // where the view was instead.
    val keys = state.rows.map { it.key }
    val lastKeys = remember { arrayOf(keys) }
    if (keys != lastKeys[0]) {
        val reorderOnly = dragging == null &&
            keys.size == lastKeys[0].size &&
            keys.toSet() == lastKeys[0].toSet()
        val index = listState.firstVisibleItemIndex
        val itemOffset = listState.firstVisibleItemScrollOffset
        lastKeys[0] = keys
        if (reorderOnly) {
            SideEffect { scope.launch { listState.scrollToItem(index, itemOffset) } }
        }
    }

    // Open on the song playing, about a third down the screen. Only on first composition, so it
    // never fights a drag or the user's own scrolling.
    LaunchedEffect(Unit) {
        val current = state.rows.indexOfFirst { it is PlaylistRow.Entry && it.current }
        if (current < 0) return@LaunchedEffect
        listState.scrollToItem(current)
        val height = snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
        listState.scrollBy(-height / 3f)
    }

    /** Swaps the dragged row with its neighbour once it's past half of it. */
    fun swapIfPastNeighbour() {
        val current = order.indexOfFirst { it.key == dragging }
        val items = listState.layoutInfo.visibleItemsInfo
        val me = items.firstOrNull { it.index == current } ?: return
        val target = if (offset > 0) {
            items.firstOrNull { it.index == current + 1 }
        } else {
            items.firstOrNull { it.index == current - 1 }
        }
        if (target == null || target.index >= songCount || abs(offset) <= target.size / 2) return
        // A list keeps its scroll anchored to the first visible row; swapping that row would
        // scroll with it and throw the drag off (phone test on #48). Pin it.
        val first = listState.firstVisibleItemIndex
        if (current == first || target.index == first) {
            val firstOffset = listState.firstVisibleItemScrollOffset
            scope.launch { listState.scrollToItem(first, firstOffset) }
        }
        order = order.toMutableList().apply { add(target.index, removeAt(current)) }
        offset += if (offset > 0) -me.size.toFloat() else me.size.toFloat()
    }

    /** Pixels to scroll this frame: negative near the top edge, positive near the bottom. */
    fun edgeStep(): Float {
        val info = listState.layoutInfo
        val me = info.visibleItemsInfo.firstOrNull { it.key == dragging } ?: return 0f
        val top = me.offset + offset
        val bottom = top + me.size
        val nearTop = info.viewportStartOffset + edgeZone - top
        val nearBottom = bottom - (info.viewportEndOffset - edgeZone)
        return when {
            nearTop > 0 -> -maxStep * (nearTop / edgeZone).coerceAtMost(1f)
            nearBottom > 0 -> maxStep * (nearBottom / edgeZone).coerceAtMost(1f)
            else -> 0f
        }
    }

    fun endDrag() {
        autoScroll?.cancel()
        autoScroll = null
        dragging = null
        offset = 0f
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PlaylistTotals(state)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(order, key = { _, row -> row.key }) { index, row ->
                val shape = groupItemShape(index, order.size)
                when (row) {
                    is PlaylistRow.Reading -> GroupedRow(shape) { ReadingRow(row) }
                    is PlaylistRow.Entry -> SongRow(
                        entry = row,
                        index = index,
                        songCount = songCount,
                        partnerName = state.partnerName,
                        shape = shape,
                        onRemove = { onRemove(row.song.id) },
                        onMove = onMove,
                        onPlay = { onPlay(index) },
                        modifier = Modifier.graphicsLayer {
                            translationY = if (dragging == row.key) offset else 0f
                        },
                        handle = Modifier.pointerInput(row.key) {
                            detectDragGestures(
                                onDragStart = {
                                    dragging = row.key
                                    dragStart = order.indexOfFirst { it.key == row.key }
                                    offset = 0f
                                    autoScroll = scope.launch {
                                        while (isActive) {
                                            withFrameNanos { }
                                            val step = edgeStep()
                                            if (step != 0f) {
                                                // The row stays under the finger as the list moves.
                                                offset += listState.scrollBy(step)
                                                swapIfPastNeighbour()
                                            }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val end = order.indexOfFirst { it.key == dragging }
                                    if (dragStart >= 0 && end >= 0 && end != dragStart) {
                                        onMove(dragStart, end)
                                    }
                                    endDrag()
                                },
                                onDragCancel = { endDrag() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    swapIfPastNeighbour()
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

/** "23 songs · 1 h 24 min" above the list. */
@Composable
private fun PlaylistTotals(state: PlaylistUiState) {
    val songs = state.rows.filterIsInstance<PlaylistRow.Entry>()
    val count = pluralStringResource(R.plurals.playlist_song_count, songs.size, songs.size)
    val totalMinutes = songs.sumOf { it.song.durationMs } / 60_000
    val length = if (totalMinutes >= 60) {
        stringResource(R.string.playlist_length_hours, totalMinutes / 60, totalMinutes % 60)
    } else {
        stringResource(R.string.playlist_length_minutes, totalMinutes)
    }
    val sharing = when (state.sharing) {
        Sharing.NotPaired -> stringResource(R.string.playlist_sharing_not_paired)
        Sharing.Shared -> state.partnerName?.let {
            stringResource(R.string.playlist_sharing_shared_named, it)
        }
            ?: stringResource(R.string.playlist_sharing_shared)
        Sharing.WaitingToSync -> stringResource(R.string.playlist_sharing_waiting)
    }
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
        Text(
            text = "$count · $length",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = sharing,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "Added by you" in C1, or the partner's phone name in C2; nothing when unpaired. */
@Composable
private fun WhoTag(entry: PlaylistRow.Entry, partnerName: String?) {
    if (partnerName == null || entry.badge == null) return
    if (entry.mine) {
        val colors = TandemMotoTheme.appColors
        Tag(stringResource(R.string.playlist_added_by_you), colors.me, colors.onMe)
    } else {
        PartnerTag(partnerName)
    }
}

/**
 * Where the song is: yellow while it moves, green once it's here, grey while it waits, red when
 * the phone is full. Just a green "On this phone" when unpaired.
 */
@Composable
private fun WhereChip(entry: PlaylistRow.Entry, partnerName: String?) {
    val colors = TandemMotoTheme.appColors
    val scheme = MaterialTheme.colorScheme
    val badge = entry.badge
    if (partnerName == null || badge == null) {
        StatusChip(stringResource(R.string.playlist_on_this_phone), colors.stored, colors.onStored)
        return
    }
    val (text, color, onColor) = when (badge) {
        SongBadge.OnBothPhones ->
            Triple(stringResource(R.string.playlist_on_both_phones), colors.stored, colors.onStored)
        SongBadge.OnThisPhone ->
            Triple(stringResource(R.string.playlist_on_this_phone), colors.stored, colors.onStored)
        is SongBadge.Sending -> Triple(
            stringResource(R.string.playlist_sending, badge.percent),
            colors.transferring,
            colors.onTransferring
        )
        is SongBadge.Receiving -> Triple(
            stringResource(R.string.playlist_receiving, badge.percent),
            colors.transferring,
            colors.onTransferring
        )
        SongBadge.NotOnPartner -> Triple(
            stringResource(R.string.playlist_not_on_partner, partnerName),
            scheme.surfaceVariant,
            scheme.onSurfaceVariant
        )
        SongBadge.Queued ->
            Triple(
                stringResource(R.string.playlist_queued),
                scheme.surfaceVariant,
                scheme.onSurfaceVariant
            )
        SongBadge.WaitingForConnection -> Triple(
            stringResource(R.string.playlist_waiting_for_connection),
            scheme.surfaceVariant,
            scheme.onSurfaceVariant
        )
        SongBadge.StorageFull ->
            Triple(stringResource(R.string.playlist_storage_full), scheme.error, scheme.onError)
        SongBadge.OnPartnerOnly -> return
    }
    StatusChip(text, color, onColor)
}

@Composable
private fun ReadingRow(row: PlaylistRow.Reading) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.name.ifBlank { stringResource(R.string.playlist_reading) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.playlist_reading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

/**
 * Three lines that never wrap: the title (scrolling sideways when too long), the artist with the
 * duration at the far right, then whose song it is and where it is. A song that can't play has
 * one red line in place of the tags. The song playing sits on `primaryContainer`.
 */
@Composable
private fun SongRow(
    entry: PlaylistRow.Entry,
    index: Int,
    songCount: Int,
    partnerName: String?,
    shape: Shape,
    onRemove: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier,
    handle: Modifier
) {
    val song = entry.song
    var menu by remember { mutableStateOf(false) }
    val moveTop = stringResource(R.string.playlist_move_top)
    val moveUp = stringResource(R.string.playlist_move_up)
    val moveDown = stringResource(R.string.playlist_move_down)
    val moveBottom = stringResource(R.string.playlist_move_bottom)
    val last = songCount - 1
    val remove = stringResource(R.string.playlist_remove)
    val playFromHere = stringResource(R.string.playlist_play_from_here)
    val nowPlaying = stringResource(R.string.playlist_now_playing)
    val problem = entry.missing || entry.cantPlay || entry.partnerCantPlay
    val scheme = MaterialTheme.colorScheme
    val secondary = if (entry.current) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    GroupedRow(
        shape = shape,
        color = if (entry.current) scheme.primaryContainer else scheme.surface,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClickLabel = playFromHere, onClick = onPlay)
                .semantics {
                    if (entry.current) stateDescription = nowPlaying
                    // Reordering without dragging, for TalkBack.
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(moveTop) {
                            onMove(index, 0)
                            true
                        }
                            .takeIf { index > 1 },
                        CustomAccessibilityAction(moveUp) {
                            onMove(index, index - 1)
                            true
                        }
                            .takeIf { index > 0 },
                        CustomAccessibilityAction(moveDown) {
                            onMove(index, index + 1)
                            true
                        }
                            .takeIf { index < last },
                        CustomAccessibilityAction(moveBottom) {
                            onMove(index, last)
                            true
                        }
                            .takeIf { index < last - 1 },
                        CustomAccessibilityAction(remove) {
                            onRemove()
                            true
                        }
                    )
                }
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = stringResource(R.string.playlist_drag),
                tint = secondary,
                modifier = handle
            )
            // Its place in the playlist (phone test on #48).
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.titleSmall,
                color = secondary,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .padding(start = 12.dp, end = 16.dp)
                    .widthIn(min = 24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (entry.current) FontWeight.Bold else null,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        song.artist ?: stringResource(R.string.playlist_unknown_artist),
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatDuration(song.durationMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondary,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (problem) {
                    Text(
                        when {
                            entry.missing -> stringResource(R.string.playlist_file_missing)
                            entry.cantPlay -> stringResource(R.string.playlist_cant_play)
                            else -> stringResource(
                                R.string.playlist_partner_cant_play,
                                partnerName ?: stringResource(R.string.ride_your_partner)
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clipToBounds()
                    ) {
                        WhoTag(entry, partnerName)
                        WhereChip(entry, partnerName)
                    }
                }
            }
            if (problem) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = scheme.error,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.playlist_more, song.title)
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    // Top and bottom only where they differ from up and down.
                    listOfNotNull(
                        (moveTop to 0).takeIf { index > 1 },
                        (moveUp to index - 1).takeIf { index > 0 },
                        (moveDown to index + 1).takeIf { index < last },
                        (moveBottom to last).takeIf { index < last - 1 }
                    ).forEach { (label, to) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            menu = false
                            onMove(index, to)
                        })
                    }
                    DropdownMenuItem(text = { Text(remove) }, onClick = {
                        menu = false
                        onRemove()
                    })
                }
            }
        }
    }
}

/** 3:45, or 1:02:03 for anything an hour or longer. */
internal fun formatDuration(ms: Long): String {
    val total = ms / 1_000
    val hours = total / 3_600
    val minutes = total % 3_600 / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private const val FEW_FILE_SLOTS = 50

/** Holding a dragged song this close to an edge scrolls the list, up to this far per frame. */
private val AUTO_SCROLL_EDGE = 72.dp
private val AUTO_SCROLL_MAX_STEP = 20.dp

@Preview(showBackground = true)
@Composable
private fun PlaylistEmptyPreview() {
    TandemMotoTheme(dynamicColor = false) {
        PlaylistScreen(PlaylistUiState(loaded = true), {}, {}, {}, {}, {}, { _, _ -> })
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaylistPreview() {
    val songs = listOf(
        Song("a", "u1", "Highway Star", "Deep Purple", 367_000, 8_000_000),
        Song("b", "u2", "Ride", null, 225_000, 6_000_000),
        Song("c", "u3", "Long Road", "Band", 301_000, 7_000_000)
    )
    TandemMotoTheme(dynamicColor = false) {
        PlaylistScreen(
            PlaylistUiState(
                rows = listOf(
                    PlaylistRow.Entry(songs[0], missing = false),
                    PlaylistRow.Entry(songs[1], missing = true),
                    PlaylistRow.Entry(songs[2], missing = false),
                    PlaylistRow.Reading("u4", "new-song.mp3")
                ),
                loaded = true
            ),
            {},
            {},
            {},
            {},
            {},
            { _, _ -> }
        )
    }
}
