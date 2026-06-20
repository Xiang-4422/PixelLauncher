package com.purride.pixellauncherv2.launcher

enum class HomeStateBlockType {
    CALENDAR,
    MEDIA,
    TODO,
    COUNTDOWN,
}

enum class HomeStateBlockMode {
    PRIMARY,
    SUMMARY,
    OFF,
}

data class HomeStateBlockConfig(
    val type: HomeStateBlockType,
    val mode: HomeStateBlockMode = HomeStateBlockMode.PRIMARY,
)

data class HomeStateBlockCandidate(
    val type: HomeStateBlockType,
    val title: String,
    val body: String,
    val priority: Int,
)

data class HomeStateBlock(
    val type: HomeStateBlockType,
    val title: String,
    val body: String,
    val isPrimary: Boolean,
)

object HomeStateBlockModel {

    fun select(
        candidates: List<HomeStateBlockCandidate>,
        configs: List<HomeStateBlockConfig> = defaultConfigs(),
    ): HomeStateBlock? {
        val configByType = configs.associateBy(HomeStateBlockConfig::type)
        return candidates
            .asSequence()
            .filter { candidate -> candidate.title.isNotBlank() || candidate.body.isNotBlank() }
            .mapNotNull { candidate ->
                val mode = configByType[candidate.type]?.mode ?: HomeStateBlockMode.OFF
                if (mode == HomeStateBlockMode.OFF) {
                    null
                } else {
                    HomeStateBlock(
                        type = candidate.type,
                        title = candidate.title.trim(),
                        body = candidate.body.trim(),
                        isPrimary = mode == HomeStateBlockMode.PRIMARY,
                    ) to candidate.priority
                }
            }
            .sortedWith(
                compareByDescending<Pair<HomeStateBlock, Int>> { (block, _) -> block.isPrimary }
                    .thenByDescending { (_, priority) -> priority },
            )
            .firstOrNull()
            ?.first
    }

    fun defaultConfigs(): List<HomeStateBlockConfig> {
        return HomeStateBlockType.entries.map { type ->
            HomeStateBlockConfig(type = type, mode = HomeStateBlockMode.OFF)
        }
    }
}
