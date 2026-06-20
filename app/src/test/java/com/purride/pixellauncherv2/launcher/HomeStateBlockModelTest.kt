package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateBlockModelTest {

    @Test
    fun defaultConfigsKeepAllOfficialBlocksOff() {
        assertEquals(
            HomeStateBlockType.entries,
            HomeStateBlockModel.defaultConfigs().map(HomeStateBlockConfig::type),
        )
        assertTrue(HomeStateBlockModel.defaultConfigs().all { it.mode == HomeStateBlockMode.OFF })
    }

    @Test
    fun selectReturnsOnlyOnePrimaryBlockByPriority() {
        val block = HomeStateBlockModel.select(
            candidates = officialCandidates(),
            configs = listOf(
                HomeStateBlockConfig(HomeStateBlockType.CALENDAR, HomeStateBlockMode.PRIMARY),
                HomeStateBlockConfig(HomeStateBlockType.MEDIA, HomeStateBlockMode.PRIMARY),
                HomeStateBlockConfig(HomeStateBlockType.TODO, HomeStateBlockMode.PRIMARY),
                HomeStateBlockConfig(HomeStateBlockType.COUNTDOWN, HomeStateBlockMode.PRIMARY),
            ),
        )

        assertEquals(HomeStateBlockType.COUNTDOWN, block?.type)
        assertTrue(block?.isPrimary == true)
    }

    @Test
    fun primaryModeWinsOverSummaryMode() {
        val block = HomeStateBlockModel.select(
            candidates = officialCandidates(),
            configs = listOf(
                HomeStateBlockConfig(HomeStateBlockType.MEDIA, HomeStateBlockMode.SUMMARY),
                HomeStateBlockConfig(HomeStateBlockType.TODO, HomeStateBlockMode.PRIMARY),
            ),
        )

        assertEquals(HomeStateBlockType.TODO, block?.type)
        assertTrue(block?.isPrimary == true)
    }

    @Test
    fun summaryModeCanStillShowWhenNoPrimaryIsAvailable() {
        val block = HomeStateBlockModel.select(
            candidates = officialCandidates(),
            configs = listOf(HomeStateBlockConfig(HomeStateBlockType.MEDIA, HomeStateBlockMode.SUMMARY)),
        )

        assertEquals(HomeStateBlockType.MEDIA, block?.type)
        assertFalse(block?.isPrimary == true)
    }

    @Test
    fun offBlocksAreIgnored() {
        val block = HomeStateBlockModel.select(
            candidates = officialCandidates(),
            configs = listOf(HomeStateBlockConfig(HomeStateBlockType.COUNTDOWN, HomeStateBlockMode.OFF)),
        )

        assertNull(block)
    }

    private fun officialCandidates(): List<HomeStateBlockCandidate> {
        return listOf(
            HomeStateBlockCandidate(HomeStateBlockType.CALENDAR, "CAL", "10:00 MEET", priority = 10),
            HomeStateBlockCandidate(HomeStateBlockType.MEDIA, "PLAY", "TRACK", priority = 20),
            HomeStateBlockCandidate(HomeStateBlockType.TODO, "TODO", "1 DUE", priority = 30),
            HomeStateBlockCandidate(HomeStateBlockType.COUNTDOWN, "TIMER", "05:00", priority = 40),
        )
    }
}
