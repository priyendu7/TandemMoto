package com.tandemmoto.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.tandemmoto.ui.theme.DarkAppColors
import com.tandemmoto.ui.theme.LightAppColors
import com.tandemmoto.ui.theme.TandemMotoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ThemeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Seen(val primary: Color, val background: Color, val card: Color)

    private fun seen(dark: Boolean): Seen {
        var seen: Seen? = null
        compose.setContent {
            TandemMotoTheme(darkTheme = dark) {
                val scheme = MaterialTheme.colorScheme
                seen = Seen(scheme.primary, scheme.background, scheme.surfaceContainer)
            }
        }
        compose.waitForIdle()
        return seen!!
    }

    @Test
    @Config(sdk = [34])
    fun darkIsTheRidePaletteEvenWhereMaterialYouExists() {
        val dark = seen(dark = true)
        assertEquals(Color(0xFFF2A33A), dark.primary)
        assertEquals(Color(0xFF0E0F11), dark.background)
        assertEquals(Color(0xFF16171A), dark.card)
    }

    @Test
    @Config(sdk = [30])
    fun lightKeepsTheAmberWithWhiteCardsOnAWarmPage() {
        val light = seen(dark = false)
        assertEquals(Color(0xFF9A5800), light.primary)
        assertEquals(Color(0xFFF5F3EE), light.background)
        assertEquals(Color.White, light.card)
    }

    private fun appColorsMe(dark: Boolean): Color {
        var me = Color.Unspecified
        compose.setContent {
            TandemMotoTheme(darkTheme = dark) { me = TandemMotoTheme.appColors.me }
        }
        compose.waitForIdle()
        return me
    }

    @Test
    @Config(sdk = [30])
    fun darkThemeProvidesDarkAppColors() = assertEquals(DarkAppColors.me, appColorsMe(true))

    @Test
    @Config(sdk = [30])
    fun lightThemeProvidesLightAppColors() = assertEquals(LightAppColors.me, appColorsMe(false))
}
