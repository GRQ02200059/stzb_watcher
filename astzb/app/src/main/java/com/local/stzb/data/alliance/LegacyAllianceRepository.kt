package com.local.stzb.data.alliance

import com.example.myapplication.LocalStzbRepository
import com.local.stzb.domain.alliance.AllianceGroup
import com.local.stzb.domain.alliance.AllianceMember
import com.local.stzb.domain.alliance.AllianceRepository
import com.local.stzb.domain.alliance.AllianceSnapshot

class LegacyAllianceRepository : AllianceRepository {
    override fun load(query: String, group: String): AllianceSnapshot {
        val stats = LocalStzbRepository.loadTeamStats()
        return AllianceSnapshot(
            totalMembers = stats.total,
            groups = stats.groups.map { AllianceGroup(it.name, it.members, it.totalPower, it.totalWuxun, it.totalWeekContribute) },
            members = LocalStzbRepository.loadTeamUsers(query, group, limit = 500).map {
                AllianceMember(it.uid, it.name, it.groupName.ifBlank { "未分组" }, it.power, it.wuxun, it.contributeWeek, it.pos)
            },
        )
    }
}
