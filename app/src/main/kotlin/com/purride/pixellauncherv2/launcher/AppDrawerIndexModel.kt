package com.purride.pixellauncherv2.launcher

data class AppDrawerIndexModel(
    val pageStartIndices: List<Int>,
    val currentPageIndex: Int,
    val currentPageApps: List<AppEntry>,
    val currentPageSelectedRow: Int,
) {
    val pageCount: Int
        get() = pageStartIndices.size

    val currentPageStartIndex: Int
        get() = pageStartIndices.getOrElse(currentPageIndex) { 0 }

    companion object {
        fun create(
            apps: List<AppEntry>,
            visibleRows: Int,
            selectedIndex: Int,
        ): AppDrawerIndexModel {
            if (apps.isEmpty()) {
                return AppDrawerIndexModel(
                    pageStartIndices = emptyList(),
                    currentPageIndex = 0,
                    currentPageApps = emptyList(),
                    currentPageSelectedRow = 0,
                )
            }

            val pageSize = visibleRows.coerceAtLeast(1)
            val pageStartIndices = buildList {
                var startIndex = 0
                while (startIndex < apps.size) {
                    add(startIndex)
                    startIndex += pageSize
                }
            }
            val safeSelectedIndex = selectedIndex.coerceIn(0, apps.lastIndex)
            val currentPageIndex = (safeSelectedIndex / pageSize).coerceIn(0, pageStartIndices.lastIndex)
            val currentPageStartIndex = pageStartIndices[currentPageIndex]
            val currentPageEndIndexExclusive = (currentPageStartIndex + pageSize).coerceAtMost(apps.size)

            return AppDrawerIndexModel(
                pageStartIndices = pageStartIndices,
                currentPageIndex = currentPageIndex,
                currentPageApps = apps.subList(currentPageStartIndex, currentPageEndIndexExclusive),
                currentPageSelectedRow = safeSelectedIndex - currentPageStartIndex,
            )
        }
    }
}
