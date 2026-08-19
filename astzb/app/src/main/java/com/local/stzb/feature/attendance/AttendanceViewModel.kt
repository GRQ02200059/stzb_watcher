package com.local.stzb.feature.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.LocalSiegeTask
import com.example.myapplication.LocalStzbRepository
import com.example.myapplication.LocalTaskAttendanceRow
import com.example.myapplication.LocalTaskBattleRow
import com.example.myapplication.LocalTaskStatisticSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface AttendanceRepository {
    fun loadTasks(): List<LocalSiegeTask>
    fun loadGroups(): List<String>
    fun create(name: String, pos: String, groups: List<String>, taskTime: Long, targetUids: List<Long> = emptyList()): LocalSiegeTask
    fun task(id: Long): LocalSiegeTask?
    fun attendance(id: Long): List<LocalTaskAttendanceRow>
    fun battles(id: Long): List<LocalTaskBattleRow>
    fun calculate(id: Long): LocalTaskStatisticSummary
    fun delete(id: Long)
}

object LocalAttendanceRepository : AttendanceRepository {
    override fun loadTasks() = LocalStzbRepository.loadSiegeTasks(0)
    override fun loadGroups() = LocalStzbRepository.loadTaskGroups()
    override fun create(name: String, pos: String, groups: List<String>, taskTime: Long, targetUids: List<Long>) =
        LocalStzbRepository.createSiegeTask(name, pos, groups, taskTime, targetUids)
    override fun task(id: Long) = LocalStzbRepository.loadSiegeTask(id)
    override fun attendance(id: Long) = LocalStzbRepository.loadTaskAttendanceForTask(id, 0)
    override fun battles(id: Long) = LocalStzbRepository.loadTaskBattles(id, 0)
    override fun calculate(id: Long) = LocalStzbRepository.refreshSiegeTaskStatistics(id)
    override fun delete(id: Long) = LocalStzbRepository.deleteSiegeTask(id)
}

data class AttendanceUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val tasks: List<LocalSiegeTask> = emptyList(),
    val groups: List<String> = emptyList(),
    val selectedTask: LocalSiegeTask? = null,
    val attendance: List<LocalTaskAttendanceRow> = emptyList(),
    val battles: List<LocalTaskBattleRow> = emptyList(),
    val message: String? = null,
    val error: String? = null,
)

class AttendanceViewModel(
    private val repository: AttendanceRepository = LocalAttendanceRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AttendanceUiState())
    val state: StateFlow<AttendanceUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() = perform {
        mutableState.value = mutableState.value.copy(tasks = repository.loadTasks(), groups = repository.loadGroups())
    }

    fun createTask(name: String, pos: String, groups: List<String>, taskTime: Long, targetUids: List<Long> = emptyList()) = perform {
        repository.create(name, pos, groups, taskTime, targetUids)
        mutableState.value = mutableState.value.copy(tasks = repository.loadTasks(), message = "任务已创建")
    }

    fun openTask(id: Long) = perform {
        val task = repository.task(id) ?: error("任务不存在")
        mutableState.value = mutableState.value.copy(selectedTask = task, attendance = repository.attendance(id), battles = repository.battles(id))
    }

    fun closeTask() { mutableState.value = mutableState.value.copy(selectedTask = null, attendance = emptyList(), battles = emptyList()) }

    fun calculateSelected() {
        val id = mutableState.value.selectedTask?.id ?: return
        perform {
            val summary = repository.calculate(id)
            val task = repository.task(id) ?: error("任务不存在")
            mutableState.value = mutableState.value.copy(
                selectedTask = task, attendance = repository.attendance(id), battles = repository.battles(id),
                tasks = repository.loadTasks(), message = "统计完成：实到 ${summary.completeUsers} 人",
            )
        }
    }

    fun deleteSelected() {
        val id = mutableState.value.selectedTask?.id ?: return
        perform {
            repository.delete(id)
            mutableState.value = mutableState.value.copy(selectedTask = null, attendance = emptyList(), battles = emptyList(), tasks = repository.loadTasks(), message = "任务已删除")
        }
    }

    fun selectedCsv(): String = attendanceCsv(mutableState.value.attendance)

    private fun perform(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = mutableState.value.tasks.isEmpty(), busy = true, error = null)
            runCatching { withContext(io) { block() } }
                .onSuccess { mutableState.value = mutableState.value.copy(loading = false, busy = false) }
                .onFailure { mutableState.value = mutableState.value.copy(loading = false, busy = false, error = it.message ?: "操作失败") }
        }
    }
}

fun attendanceCsv(rows: List<LocalTaskAttendanceRow>): String = buildString {
    append('\uFEFF')
    appendLine("名字,分组,主力队,拆迁队,主力次数,拆迁次数,状态")
    rows.sortedByDescending { it.atkNum + it.disNum }.forEach { row ->
        appendLine(listOf(row.name, row.groupName, row.atkTeamNum, row.disTeamNum, row.atkNum, row.disNum, if (row.atkNum > 0 || row.disNum > 0) "出战" else "缺勤").joinToString(",") { it.toString().replace(",", "，") })
    }
}
