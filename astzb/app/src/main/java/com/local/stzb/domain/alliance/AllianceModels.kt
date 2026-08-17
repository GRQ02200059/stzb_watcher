package com.local.stzb.domain.alliance

data class AllianceMember(
    val uid: Long,
    val name: String,
    val groupName: String,
    val power: Int,
    val wuxun: Int,
    val weeklyContribution: Int,
    val position: Int,
)

data class AllianceGroup(
    val name: String,
    val members: Int,
    val totalPower: Long,
    val totalWuxun: Long,
    val weeklyContribution: Long,
)

data class AllianceSnapshot(
    val totalMembers: Int,
    val groups: List<AllianceGroup>,
    val members: List<AllianceMember>,
)

interface AllianceRepository {
    fun load(query: String = "", group: String = ""): AllianceSnapshot
}
