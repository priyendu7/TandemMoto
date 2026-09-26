package com.tandemmoto.ui.playlist

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tandemmoto.R
import com.tandemmoto.library.ImportSummary
import com.tandemmoto.library.Song
import com.tandemmoto.ui.components.BackTopBar
import com.tandemmoto.ui.theme.TandemMotoTheme
import kotlin.math.abs
import kotlinx.coroutines.Job
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
        onMove = viewModel::move
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
    snackbar: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        topBar = {
            if (state.rows.isEmpty()) {
                BackTopBar(stringResource(R.string.playlist_title), onBack)
            } else {
                PlaylistTopBar(state, onBack, onAddSongs, onAddFolder, onCheckFolders)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !state.loaded -> Unit
                state.rows.isEmpty() -> EmptyPlaylist(onAddSongs, onAddFolder)
                else -> SongList(state, onRemove, onMove)
            }
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
    onMove: (Int, Int) -> Unit
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
        HorizontalDivider()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(order, key = { _, row -> row.key }) { index, row ->
                when (row) {
                    is PlaylistRow.Reading -> ReadingRow(row)
                    is PlaylistRow.Entry -> SongRow(
                        entry = row,
                        index = index,
                        songCount = songCount,
                        onRemove = { onRemove(row.song.id) },
                        onMove = onMove,
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
                HorizontalDivider()
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
    Text(
        text = "$count · $length",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun ReadingRow(row: PlaylistRow.Reading) {
    ListItem(
        headlineContent = { Text(row.name.ifBlank { stringResource(R.string.playlist_reading) }) },
        supportingContent = { Text(stringResource(R.string.playlist_reading)) },
        trailingContent = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
    )
}

@Composable
private fun SongRow(
    entry: PlaylistRow.Entry,
    index: Int,
    songCount: Int,
    onRemove: () -> Unit,
    onMove: (Int, Int) -> Unit,
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
    val details = stringResource(
        R.string.playlist_song_details,
        song.artist ?: stringResource(R.string.playlist_unknown_artist),
        formatDuration(song.durationMs)
    )
    ListItem(
        modifier = modifier.semantics {
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
        },
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.playlist_drag),
                    modifier = handle
                )
                // Its place in the playlist (phone test on #48).
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 24.dp)
                )
            }
        },
        headlineContent = { Text(song.title) },
        supportingContent = {
            if (entry.missing) {
                Text(
                    stringResource(R.string.playlist_file_missing),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text("$details · ${stringResource(R.string.playlist_on_this_phone)}")
            }
        },
        trailingContent = {
            Box {
                if (entry.missing) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 48.dp)
                    )
                }
                IconButton(onClick = {
                    menu = true
                }, modifier = Modifier.align(Alignment.CenterEnd)) {
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
    )
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
