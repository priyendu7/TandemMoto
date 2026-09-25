package com.tandemmoto.link

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PartnerStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.store(file: File = File(tmp.root, "partner.preferences_pb")) =
        DataStorePartnerStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        )

    private val partner = Partner("Redmi Y2", "aa:bb:cc:dd:ee:ff", Partner.Role.Initiator, 42L)

    @Test
    fun emptyStoreHasNoPartner() = runTest {
        assertNull(store().partner.first())
    }

    @Test
    fun savedPartnerReadsBack() = runTest {
        val store = store()
        store.save(partner)
        assertEquals(partner, store.partner.first())
    }

    @Test
    fun clearForgetsThePartner() = runTest {
        val store = store()
        store.save(partner)
        store.clear()
        assertNull(store.partner.first())
    }

    @Test
    fun matchesByAddressOrElseByName() {
        assertTrue(partner.matches(phone("Renamed", partner.address)))
        assertTrue(partner.matches(phone(partner.name, "new-address")))
        assertFalse(partner.matches(phone("Galaxy S25", "other")))
        assertFalse(partner.matches(phone("", "")))
    }
}
