package com.purride.pixeldemo.catalog

object DemoCatalog {
    val sections: List<DemoSection> = emptyList()

    fun findById(id: String): DemoScene? =
        sections.flatMap { it.scenes }.find { it.id == id }
}
