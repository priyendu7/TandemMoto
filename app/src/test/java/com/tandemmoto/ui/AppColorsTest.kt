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

    private fun pairs(c: AppColors) = mapOf(
        "me" to (c.me to c.onMe),
        "partner" to (c.partner to c.onPartner),
        "connected" to (c.connected to c.onConnected),
        "searching" to (c.searching to c.onSearching),
        "notConnected" to (c.notConnected to c.onNotConnected),
        "transferring" to (c.transferring to c.onTransferring),
        "stored" to (c.stored to c.onStored),
        "connectedTint" to (c.connectedTint to c.onConnectedTint),
        "searchingTint" to (c.searchingTint to c.onSearchingTint),
        "notConnectedTint" to (c.notConnectedTint to c.onNotConnectedTint)
    )

    private fun assertReadable(mode: String, colors: AppColors) {
        pairs(colors).forEach { (name, pair) ->
            val ratio = contrast(pair.first, pair.second)
            assertTrue("$mode $name contrast $ratio < 4.5", ratio >= 4.5f)
        }
    }

    @Test
    fun lightColorsMeetContrast() = assertReadable("light", LightAppColors)

    @Test
    fun darkColorsMeetContrast() = assertReadable("dark", DarkAppColors)
}
