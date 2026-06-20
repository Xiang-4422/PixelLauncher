package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.viewmodel.LauncherUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeInfoDetailModelTest {

    @Test
    fun rainNoticeReflectsPermissionAndRefreshTime() {
        assertEquals(
            "RAIN NEEDS LOC",
            HomeInfoDetailModel.notice(HomeInfoAction.RAIN, LauncherState(hasLocationPermission = false)),
        )
        assertEquals(
            "RAIN UPDATED 09:41",
            HomeInfoDetailModel.notice(
                HomeInfoAction.RAIN,
                LauncherState(hasLocationPermission = true, rainUpdatedTimeText = "09:41"),
            ),
        )
        assertEquals(
            "RAIN REFRESH",
            HomeInfoDetailModel.notice(HomeInfoAction.RAIN, LauncherState(hasLocationPermission = true)),
        )
    }

    @Test
    fun usageNoticeReflectsAccessState() {
        assertEquals(
            "USE NEEDS ACCESS",
            HomeInfoDetailModel.notice(HomeInfoAction.USAGE, LauncherState(hasUsageAccess = false)),
        )
        assertEquals(
            "USE TODAY",
            HomeInfoDetailModel.notice(HomeInfoAction.USAGE, LauncherState(hasUsageAccess = true)),
        )
    }

    @Test
    fun rainRefreshFeedbackIsShortAndStateful() {
        assertEquals("RAIN REFRESHING", HomeInfoDetailModel.rainRefreshStarted())
        assertEquals(
            "RAIN UPDATED 09:41",
            HomeInfoDetailModel.rainRefreshUpdated(LauncherState(rainUpdatedTimeText = "09:41")),
        )
        assertEquals("RAIN KEEP LAST", HomeInfoDetailModel.rainRefreshFailed(hasPreviousHint = true))
        assertEquals("RAIN UNAVAILABLE", HomeInfoDetailModel.rainRefreshFailed(hasPreviousHint = false))
        assertEquals("RAIN NO LOCATION", HomeInfoDetailModel.rainLocationUnavailable())
    }

    @Test
    fun usageRefreshFeedbackShowsCurrentCountersWhenAvailable() {
        assertEquals("USE REFRESHING", HomeInfoDetailModel.usageRefreshStarted())
        assertEquals(
            "USE 00:20 OPEN 3",
            HomeInfoDetailModel.usageRefreshResult(
                LauncherState(
                    hasUsageAccess = true,
                    screenUsageTimeText = "00:20",
                    screenOpenCountText = "3",
                ),
            ),
        )
        assertEquals(
            "USE NEEDS ACCESS",
            HomeInfoDetailModel.usageRefreshResult(LauncherState(hasUsageAccess = false)),
        )
    }

    @Test
    fun communicationAndDeviceNoticesAreShortActions() {
        assertEquals(
            "OPEN UNREAD SMS",
            HomeInfoDetailModel.notice(HomeInfoAction.SMS, LauncherState(unreadSmsCount = 2)),
        )
        assertEquals("OPEN SMS", HomeInfoDetailModel.notice(HomeInfoAction.SMS, LauncherState()))
        assertEquals("OPEN CALL LOG", HomeInfoDetailModel.notice(HomeInfoAction.CALL, LauncherState()))
        assertEquals("OPEN ALARMS", HomeInfoDetailModel.notice(HomeInfoAction.ALARM, LauncherState()))
        assertEquals("OPEN BATTERY", HomeInfoDetailModel.notice(HomeInfoAction.BATTERY, LauncherState()))
        assertEquals("OPEN NOTIFY", HomeInfoDetailModel.notice(HomeInfoAction.NOTIFICATION, LauncherState()))
    }

    @Test
    fun uiStateOverloadMatchesLauncherStateOverload() {
        val launcherState = LauncherState(
            hasLocationPermission = true,
            rainUpdatedTimeText = "09:41",
        )
        val uiState = LauncherUiState(
            hasLocationPermission = true,
            rainUpdatedTimeText = "09:41",
        )

        assertEquals(
            HomeInfoDetailModel.notice(HomeInfoAction.RAIN, launcherState),
            HomeInfoDetailModel.notice(HomeInfoAction.RAIN, uiState),
        )
    }
}
