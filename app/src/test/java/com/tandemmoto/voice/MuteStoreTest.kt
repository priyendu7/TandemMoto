package com.tandemmoto.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MuteStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun muteIsRememberedAcrossRestarts() {
        assertFalse(SharedPrefsMuteStore(context).muted.value)
        SharedPrefsMuteStore(context).set(true)
        assertTrue(
            "a new store, as after an app restart",
            SharedPrefsMuteStore(context).muted.value
        )
        SharedPrefsMuteStore(context).set(false)
        assertFalse(SharedPrefsMuteStore(context).muted.value)
    }
}
