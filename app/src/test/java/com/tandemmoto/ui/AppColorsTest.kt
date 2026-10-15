package com.tandemmoto.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.tandemmoto.ui.theme.AppColors
import com.tandemmoto.ui.theme.DarkAppColors
import com.tandemmoto.ui.theme.LightAppColors
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Test

class AppColorsTest {
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    /** Text on a fill: 4.5:1. */
    private fun pairs(c: AppColors) = mapOf(
        "me" to (c.me to c.onMe),
        "partner" to (c.partner to c.onPartner),
        "accent" to (c.accent to c.onAccent),
        "connected" to (c.connected to c.onConnected),
        "searching" to (c.searching to c.onSearching),
        "notConnected" to (c.notConnected to c.onNotConnected),
        "idle" to (c.idle to c.onIdle),
        "transferring" to (c.transferring to c.onTransferring),
        "stored" to (c.stored to c.onStored),
        // Start and Stop intercom write the status colour on its tint.
        "connected on tint" to (c.connected to c.connectedTint),
        "notConnected on tint" to (c.notConnected to c.notConnectedTint)
    )

    /** A status icon on its tile: 3:1. */
    private fun icons(c: AppColors) = mapOf(
        "connected" to (c.connected to c.connectedTint),
        "searching" to (c.searching to c.searchingTint),
        "notConnected" to (c.notConnected to c.notConnectedTint),
        "idle" to (c.idle to c.idleTint)
    )

    private fun assertReadable(mode: String, colors: AppColors) {
        pairs(colors).forEach { (name, pair) ->
            val ratio = contrast(pair.first, pair.second)
            assertTrue("$mode $name contrast $ratio < 4.5", ratio >= 4.5f)
        }
        icons(colors).forEach { (name, pair) ->
            val ratio = contrast(pair.first, pair.second)
            assertTrue("$mode $name icon contrast $ratio < 3", ratio >= 3f)
        }
    }

    @Test
    fun lightColorsMeetContrast() = assertReadable("light", LightAppColors)

    @Test
    fun darkColorsMeetContrast() = assertReadable("dark", DarkAppColors)
}
