package com.local.stzb.data.intel

import com.example.myapplication.LocalStzbRepository
import com.local.stzb.domain.intel.AnnouncementSummary
import com.local.stzb.domain.intel.IntelRepository
import com.local.stzb.domain.intel.IntelSnapshot
import com.local.stzb.domain.intel.MapCellSummary

class LegacyIntelRepository : IntelRepository {
    override fun load(mapQuery: String): IntelSnapshot {
        val stats = LocalStzbRepository.loadMapStats()
        val query = mapQuery.trim()
        val cells = LocalStzbRepository.loadMapCells(cityName = query, limit = 300)
            .filter { query.isBlank() || it.cityName.contains(query, true) || it.wid.toString().contains(query) }
            .map {
                MapCellSummary(
                    wid = it.wid,
                    coordinates = if (it.x != 0 || it.y != 0) "${it.x},${it.y}" else "${it.wid / 10_000},${it.wid % 10_000}",
                    typeName = it.typeName.ifBlank { "类型 ${it.cellType}" },
                    cityName = it.cityName,
                    ownerName = it.ownerName,
                )
            }
        val announcements = LocalStzbRepository.loadAnnouncements(80).map {
            AnnouncementSummary(it.annId, it.title.ifBlank { "游戏公告" }, it.content, it.pubTime)
        }
        return IntelSnapshot(stats.totalCells, stats.namedCities, cells, announcements)
    }
}
