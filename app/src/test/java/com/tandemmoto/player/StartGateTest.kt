package com.tandemmoto.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StartGateTest {
    private fun decide(
        linked: Boolean = true,
        hereReady: Boolean = true,
        cantPlayHere: Set<String> = emptySet(),
        partner: PartnerSongs = PartnerSongs(has = setOf("a")),
        partnerPlaying: Boolean = false
    ) = StartGate.decide("a", linked, hereReady, cantPlayHere, partner, partnerPlaying)

    @Test
    fun onBothPhonesItPlays() {
        assertEquals(StartDecision.Play, decide())
    }

    @Test
    fun notHereYetItHoldsForThisPhone() {
        assertEquals(StartDecision.Hold(MissingOn.ThisPhone), decide(hereReady = false))
    }

    @Test
    fun notOnThePartnersPhoneItHoldsForThePartner() {
        assertEquals(
            StartDecision.Hold(MissingOn.Partner),
            decide(partner = PartnerSongs(has = emptySet()))
        )
    }

    @Test
    fun thePartnerPlayingItMeansItHasIt() {
        // Its song list may still be on its way.
        assertEquals(
            StartDecision.Play,
            decide(partner = PartnerSongs(has = emptySet()), partnerPlaying = true)
        )
    }

    @Test
    fun notLinkedEachPhonePlaysOnItsOwn() {
        assertEquals(
            StartDecision.Play,
            decide(linked = false, hereReady = false, partner = PartnerSongs())
        )
    }

    @Test
    fun aSongEitherPhoneCantPlayIsSkipped() {
        assertEquals(StartDecision.Skip, decide(cantPlayHere = setOf("a")))
        assertEquals(StartDecision.Skip, decide(cantPlayHere = setOf("a"), linked = false))
        assertEquals(
            StartDecision.Skip,
            decide(partner = PartnerSongs(has = setOf("a"), cantPlay = setOf("a")))
        )
    }

    @Test
    fun thePartnersCantPlayDoesntCountWhenNotLinked() {
        assertEquals(
            StartDecision.Play,
            decide(linked = false, partner = PartnerSongs(cantPlay = setOf("a")))
        )
    }
}
