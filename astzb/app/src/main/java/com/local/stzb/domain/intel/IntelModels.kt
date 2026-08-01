package com.local.stzb.domain.intel

data class MapCellSummary(
    val wid: Int,
    val coordinates: String,
    val typeName: String,
    val cityName: String,
    val ownerName: String,
)

data class AnnouncementSummary(
    val id: Long,
    val title: String,
    val content: String,
    val publishedAt: Long,
)

data class IntelSnapshot(
    val totalCells: Int,
    val namedCities: Int,
    val cells: List<MapCellSummary>,
    val announcements: List<AnnouncementSummary>,
)

interface IntelRepository {
    fun load(mapQuery: String = ""): IntelSnapshot
}
