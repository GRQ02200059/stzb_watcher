package com.local.stzb.domain.battlefield

import kotlinx.coroutines.flow.Flow

interface BattlefieldRepository {
    fun observeSnapshot(): Flow<BattlefieldSnapshot>
    suspend fun refresh()
    fun setPaused(paused: Boolean)
    fun setFilter(categories: Set<EventCategory>)
}
