package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFixedInfoModelTest {

    @Test
    fun rowsOmitAlarmWhenNoAlarmIsAvailable() {
        val rows = HomeFixedInfoModel.rows(
            LauncherState(
                nextAlarmText = "--:--",
                rainHintText = "CLEAR 23C",
            ),
        )

        assertEquals(HomeFixedInfoRowType.WEATHER, rows.first().type)
    }

    @Test
    fun rowsKeepAlarmWhenAlarmExists() {
        val rows = HomeFixedInfoModel.rows(
            LauncherState(
                nextAlarmText = "07:30",
                rainHintText = "CLEAR 23C",
            ),
        )

        assertEquals(HomeFixedInfoRowType.WEATHER, rows.first().type)
        assertEquals(HomeFixedInfoRowType.ALARM, rows[1].type)
    }
}
