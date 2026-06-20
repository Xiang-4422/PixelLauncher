package com.purride.pixellauncherv2.data

import com.purride.pixellauncherv2.launcher.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCustomizationModelTest {

    @Test
    fun parseAliases_splitsCommaWhitespaceChineseCommaAndDedupes() {
        assertEquals(
            listOf("pay", "bank", "账单", "bill"),
            AppCustomizationModel.parseAliases(" pay, bank，账单  pay\nbill "),
        )
    }

    @Test
    fun parseAliases_keepsOnlyEightAliases() {
        val aliases = AppCustomizationModel.parseAliases("a b c d e f g h i j")

        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h"), aliases)
    }

    @Test
    fun fromDraft_trimsDisplayNameAndNormalizesAliases() {
        val customization = AppCustomizationModel.fromDraft(
            labelOverride = "  Pay  ",
            aliasText = "bank bill bank",
        )

        assertEquals("Pay", customization.labelOverride)
        assertEquals(listOf("bank", "bill"), customization.aliases)
    }

    @Test
    fun applyCustomizations_overridesOnlyMatchingApp() {
        val bank = AppEntry(
            label = "Bank",
            packageName = "com.example.bank",
            activityName = "BankActivity",
            englishLabel = "Bank",
            systemLabel = "Bank",
        )
        val maps = AppEntry(
            label = "Maps",
            packageName = "com.example.maps",
            activityName = "MapsActivity",
            englishLabel = "Maps",
            systemLabel = "Maps",
        )

        val result = AppCustomizationModel.applyCustomizations(
            apps = listOf(bank, maps),
            customizations = mapOf(
                AppCustomizationModel.identity(bank) to AppCustomization(
                    labelOverride = "Pay",
                    aliases = listOf("bill", "card"),
                ),
            ),
        )

        assertEquals("Pay", result[0].label)
        assertEquals("Bank", result[0].systemLabel)
        assertEquals(listOf("bill", "card"), result[0].aliases)
        assertEquals(maps, result[1])
    }

    @Test
    fun applyCustomizations_blankDisplayNameFallsBackToSystemLabel() {
        val app = AppEntry(
            label = "Old",
            packageName = "com.example.app",
            activityName = "MainActivity",
            englishLabel = "Example",
            systemLabel = "System Name",
        )

        val result = AppCustomizationModel.applyCustomizations(
            apps = listOf(app),
            customizations = mapOf(
                AppCustomizationModel.identity(app) to AppCustomization(
                    labelOverride = "",
                    aliases = listOf("quick"),
                ),
            ),
        )

        assertEquals("System Name", result.single().label)
        assertEquals(listOf("quick"), result.single().aliases)
    }
}
