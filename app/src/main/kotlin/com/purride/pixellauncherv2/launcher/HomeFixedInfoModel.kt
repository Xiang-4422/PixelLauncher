package com.purride.pixellauncherv2.launcher

enum class HomeFixedInfoRowType {
    ALARM,
    WEATHER,
    COMMUNICATION,
    USAGE,
}

data class HomeFixedInfoRow(
    val type: HomeFixedInfoRowType,
    val text: String,
)

object HomeFixedInfoModel {

    fun rows(state: LauncherState): List<HomeFixedInfoRow> {
        return buildList {
            add(
                HomeFixedInfoRow(
                    type = HomeFixedInfoRowType.WEATHER,
                    text = state.rainHintText.ifBlank { "--" },
                ),
            )
            if (hasVisibleAlarm(state.nextAlarmText)) {
                add(
                    HomeFixedInfoRow(
                        type = HomeFixedInfoRowType.ALARM,
                        text = "ALARM ${state.nextAlarmText}",
                    ),
                )
            }
            communicationText(state)?.let { text ->
                add(
                    HomeFixedInfoRow(
                        type = HomeFixedInfoRowType.COMMUNICATION,
                        text = text,
                    ),
                )
            }
            add(
                HomeFixedInfoRow(
                    type = HomeFixedInfoRowType.USAGE,
                    text = "USE ${state.screenUsageTimeText.ifBlank { "--:--" }}  OPEN ${state.screenOpenCountText.ifBlank { "--" }}",
                ),
            )
        }
    }

    fun communicationSegments(state: LauncherState): List<String> {
        return buildList {
            if (state.missedCallCount > 0) {
                add("CALL ${state.missedCallCount}")
            }
            if (state.unreadSmsCount > 0) {
                add("SMS ${state.unreadSmsCount}")
            }
        }
    }

    private fun communicationText(state: LauncherState): String? {
        val segments = communicationSegments(state)
        return if (segments.isEmpty()) null else segments.joinToString(separator = "  ")
    }

    private fun hasVisibleAlarm(nextAlarmText: String): Boolean {
        return nextAlarmText.isNotBlank() && nextAlarmText != "--:--"
    }
}
