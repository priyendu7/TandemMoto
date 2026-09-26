package com.tandemmoto.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tandemmoto.R
import com.tandemmoto.library.Song
import com.tandemmoto.ui.playlist.PlaylistRow
import com.tandemmoto.ui.playlist.PlaylistScreen
import com.tandemmoto.ui.playlist.PlaylistUiState
import com.tandemmoto.ui.playlist.Sharing
import com.tandemmoto.ui.playlist.SongBadge
import com.tandemmoto.ui.playlist.formatDuration
import com.tandemmoto.ui.theme.TandemMotoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val alpha = Song("a", "u1", "Alpha", "Artist", 225_000, 5_000_000)
    private val bravo = Song("b", "u2", "Bravo", null, 61_000, 3_000_000)
    private val charlie = Song("c", "u3", "Charlie", "Band", 3_723_000, 9_000_000)
    private var addedSongs = false
    private var addedFolder = false
    private val removed = mutableListOf<String>()
    private val moves = mutableListOf<Pair<Int, Int>>()

    private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private fun show(state: PlaylistUiState) = compose.setContent {
        TandemMotoTheme {
            PlaylistScreen(
                state = state,
                onBack = {},
                onAddSongs = { addedSongs = true },
                onAddFolder = { addedFolder = true },
                onCheckFolders = {},
                onRemove = { removed += it },
                onMove = { from, to -> moves += from to to }
            )
        }
    }

    private fun songs(vararg entries: PlaylistRow) =
        PlaylistUiState(rows = entries.toList(), loaded = true)

    @Test
    fun anEmptyPlaylistOffersBothWaysToAdd() {
        show(PlaylistUiState(loaded = true))
        compose.onNodeWithText(str(R.string.playlist_empty)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.playlist_add_songs)).performClick()
        compose.onNodeWithText(str(R.string.playlist_add_folder)).performClick()
        assertTrue(addedSongs && addedFolder)
    }

    @Test
    fun rowsShowTitleArtistDurationAndStatus() {
        show(songs(PlaylistRow.Entry(alpha, false), PlaylistRow.Entry(bravo, true)))
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithText("Artist · 3:45 · ${str(R.string.playlist_on_this_phone)}")
            .assertIsDisplayed()
        compose.onNodeWithText(str(R.string.playlist_file_missing)).assertIsDisplayed()
    }

    @Test
    fun theTotalCountAndLengthAreShown() {
        show(songs(PlaylistRow.Entry(alpha, false), PlaylistRow.Entry(charlie, false)))
        // 3:45 + 1:02:03 = 65 min
        compose.onNodeWithText("2 songs · 1 h 5 min").assertIsDisplayed()
    }

    @Test
    fun eachSongShowsItsPlaceInThePlaylist() {
        show(songs(PlaylistRow.Entry(alpha, false), PlaylistRow.Entry(bravo, false)))
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
    }

    @Test
    fun rowsSayWhoseSongItIsOnceThereIsAPartner() {
        show(
            PlaylistUiState(
                rows = listOf(
                    PlaylistRow.Entry(alpha, false, mine = true, badge = SongBadge.OnBothPhones),
                    PlaylistRow.Entry(bravo, false, false, true, SongBadge.OnThisPhone),
                    PlaylistRow.Entry(charlie, false, false, false, SongBadge.OnPartnerOnly)
                ),
                loaded = true,
                sharing = Sharing.Shared,
                partnerName = "Redmi"
            )
        )
        val from = str(R.string.playlist_from_named, "Redmi")
        val yours = str(R.string.playlist_added_by_you)
        val both = str(R.string.playlist_on_both_phones)
        val unknown = str(R.string.playlist_unknown_artist)
        val here = str(R.string.playlist_on_this_phone)
        compose.onNodeWithText("Artist · 3:45 · $yours · $both").assertIsDisplayed()
        compose.onNodeWithText("$unknown · 1:01 · $from · $here").assertIsDisplayed()
        compose.onNodeWithText("Band · 1:02:03 · $from").assertIsDisplayed()
        compose.onNodeWithText(
            str(R.string.playlist_sharing_shared_named, "Redmi")
        ).assertIsDisplayed()
    }

    @Test
    fun eachTransferStatusHasItsLine() {
        val badges = listOf(
            SongBadge.NotOnPartner to str(R.string.playlist_not_on_partner, "Redmi"),
            SongBadge.Sending(45) to str(R.string.playlist_sending, 45),
            SongBadge.Receiving(12) to str(R.string.playlist_receiving, 12),
            SongBadge.Queued to str(R.string.playlist_queued),
            SongBadge.WaitingForConnection to str(R.string.playlist_waiting_for_connection),
            SongBadge.StorageFull to str(R.string.playlist_storage_full)
        )
        // One row at a time: a lazy list only builds the rows that fit the test screen.
        val row = mutableStateOf<PlaylistRow>(PlaylistRow.Entry(alpha, false))
        compose.setContent {
            TandemMotoTheme {
                PlaylistScreen(
                    state = PlaylistUiState(
                        rows = listOf(row.value),
                        loaded = true,
                        sharing = Sharing.Shared,
                        partnerName = "Redmi"
                    ),
                    onBack = {},
                    onAddSongs = {},
                    onAddFolder = {},
                    onCheckFolders = {},
                    onRemove = {},
                    onMove = { _, _ -> }
                )
            }
        }
        badges.forEach { (badge, text) ->
            val mine = badge is SongBadge.NotOnPartner || badge is SongBadge.Sending
            row.value = PlaylistRow.Entry(alpha, false, mine, false, badge)
            compose.onNodeWithText(text, substring = true).assertExists()
        }
        assertEquals("Sending 45%", str(R.string.playlist_sending, 45))
    }

    @Test
    fun theHeaderSaysWhenChangesWillSync() {
        show(
            songs(
                PlaylistRow.Entry(alpha, false)
            ).copy(sharing = Sharing.WaitingToSync, partnerName = "Redmi")
        )
        compose.onNodeWithText(str(R.string.playlist_sharing_waiting)).assertIsDisplayed()
    }

    @Test
    fun withoutAPartnerItSaysToPair() {
        show(songs(PlaylistRow.Entry(alpha, false)))
        compose.onNodeWithText(str(R.string.playlist_sharing_not_paired)).assertIsDisplayed()
    }

    @Test
    fun aSongBeingReadShowsReading() {
        show(songs(PlaylistRow.Reading("u9", "new-song.mp3")))
        compose.onNodeWithText("new-song.mp3").assertIsDisplayed()
    }

    @Test
    fun theRowMenuMovesAndRemoves() {
        show(
            songs(
                PlaylistRow.Entry(alpha, false),
                PlaylistRow.Entry(bravo, false),
                PlaylistRow.Entry(charlie, false)
            )
        )
        compose.onNodeWithContentDescription(str(R.string.playlist_more, "Bravo")).performClick()
        compose.onNodeWithText(str(R.string.playlist_move_up)).performClick()
        compose.onNodeWithContentDescription(str(R.string.playlist_more, "Bravo")).performClick()
        compose.onNodeWithText(str(R.string.playlist_remove)).performClick()
        assertEquals(listOf(1 to 0), moves)
        assertEquals(listOf("b"), removed)
    }

    @Test
    fun theFirstSongCannotMoveUp() {
        show(songs(PlaylistRow.Entry(alpha, false), PlaylistRow.Entry(bravo, false)))
        compose.onNodeWithContentDescription(str(R.string.playlist_more, "Alpha")).performClick()
        compose.onNodeWithText(str(R.string.playlist_move_up)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.playlist_move_down)).assertIsDisplayed()
    }

    @Test
    fun talkBackCanReorderWithoutDragging() {
        show(songs(PlaylistRow.Entry(alpha, false), PlaylistRow.Entry(bravo, false)))
        fun labels(title: String) = compose.onNode(
            hasAnyDescendant(hasText(title)).or(hasText(title)).and(
                SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)
            )
        ).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(
            listOf(str(R.string.playlist_move_down), str(R.string.playlist_remove)),
            labels("Alpha")
        )
        assertEquals(
            listOf(str(R.string.playlist_move_up), str(R.string.playlist_remove)),
            labels("Bravo")
        )
    }

    @Test
    fun moveToTopAndBottomAppearWhereTheyDifferFromUpAndDown() {
        show(
            songs(
                PlaylistRow.Entry(alpha, false),
                PlaylistRow.Entry(bravo, false),
                PlaylistRow.Entry(charlie, false),
                PlaylistRow.Entry(alpha.copy(id = "d", title = "Delta"), false)
            )
        )
        compose.onNodeWithContentDescription(str(R.string.playlist_more, "Charlie")).performClick()
        compose.onNodeWithText(str(R.string.playlist_move_top)).performClick()
        compose.onNodeWithContentDescription(str(R.string.playlist_more, "Bravo")).performClick()
        compose.onNodeWithText(str(R.string.playlist_move_bottom)).performClick()
        assertEquals(listOf(2 to 0, 1 to 3), moves)

        // Second from the top: "Move up" already goes to the top, so no "Move to top".
        compose.onNodeWithContentDescription(str(R.string.playlist_more, "Bravo")).performClick()
        compose.onNodeWithText(str(R.string.playlist_move_top)).assertDoesNotExist()
    }

    @Test
    fun movingTheTopRowToTheBottomKeepsTheViewWhereItWas() {
        // Phone test on #48: Move to bottom on the row at the top of the screen scrolled along.
        val many = (1..40).map { Song("s$it", "u$it", "Song $it", "Artist", 200_000, 1) }
        val rows = mutableStateOf(many.map { PlaylistRow.Entry(it, false) as PlaylistRow })
        compose.setContent {
            TandemMotoTheme {
                PlaylistScreen(
                    state = PlaylistUiState(rows = rows.value, loaded = true),
                    onBack = {},
                    onAddSongs = {},
                    onAddFolder = {},
                    onCheckFolders = {},
                    onRemove = {},
                    onMove = { from, to ->
                        rows.value = rows.value.toMutableList().apply { add(to, removeAt(from)) }
                    }
                )
            }
        }
        compose.onNodeWithText("Song 1").assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.playlist_more, "Song 1")).performClick()
        compose.onNodeWithText(str(R.string.playlist_move_bottom)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Song 2").assertIsDisplayed() // still at the top, not at song 40
        compose.onNodeWithText("Song 1").assertIsNotDisplayed()
    }

    @Test
    fun tappingASongPlaysFromThereAndTheCurrentOneIsMarked() {
        var played = -1
        compose.setContent {
            TandemMotoTheme {
                PlaylistScreen(
                    state = songs(
                        PlaylistRow.Entry(alpha, false, current = true),
                        PlaylistRow.Entry(bravo, false)
                    ),
                    onBack = {},
                    onAddSongs = {},
                    onAddFolder = {},
                    onCheckFolders = {},
                    onRemove = {},
                    onMove = { _, _ -> },
                    onPlay = { played = it }
                )
            }
        }
        compose.onNodeWithText(str(R.string.playlist_now_playing), substring = true).assertExists()
        compose.onNodeWithText("Bravo").performClick()
        assertEquals(1, played)
    }

    @Test
    fun aSongThisPhoneCantDecodeSaysSo() {
        show(songs(PlaylistRow.Entry(alpha, false, cantPlay = true)))
        compose.onNodeWithText(str(R.string.playlist_cant_play)).assertIsDisplayed()
    }

    @Test
    fun durationsReadLikeAClock() {
        assertEquals("3:45", formatDuration(225_000))
        assertEquals("0:05", formatDuration(5_400))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }
}
