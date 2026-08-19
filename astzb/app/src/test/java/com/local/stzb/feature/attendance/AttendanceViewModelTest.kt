package com.local.stzb.feature.attendance

import com.example.myapplication.LocalSiegeTask
import com.example.myapplication.LocalTaskAttendanceRow
import com.example.myapplication.LocalTaskBattleRow
import com.example.myapplication.LocalTaskStatisticSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun createOpenCalculateExportAndDeleteAreDelegated() = runTest {
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, dispatcher)
        advanceUntilIdle()

        viewModel.createTask("洛阳攻城", "10,20", listOf("一团"), 123L)
        advanceUntilIdle()
        assertEquals(1, repository.creates)
        assertEquals(1, viewModel.state.value.tasks.size)

        viewModel.openTask(1L)
        advanceUntilIdle()
        assertEquals(1L, viewModel.state.value.selectedTask?.id)
        assertEquals(1, viewModel.state.value.attendance.size)
        assertEquals(1, viewModel.state.value.battles.size)

        viewModel.calculateSelected()
        advanceUntilIdle()
        assertEquals(1, repository.calculations)
        assertTrue(viewModel.state.value.message.orEmpty().contains("统计完成"))

        val csv = viewModel.selectedCsv()
        assertTrue(csv.startsWith("\uFEFF名字,分组"))
        assertTrue(csv.contains("测试成员,一团"))

        viewModel.deleteSelected()
        advanceUntilIdle()
        assertEquals(listOf(1L), repository.deletes)
        assertEquals(null, viewModel.state.value.selectedTask)
        assertFalse(viewModel.state.value.busy)
    }

    private class FakeAttendanceRepository : AttendanceRepository {
        var creates = 0
        var calculations = 0
        val deletes = mutableListOf<Long>()
        private var tasks = emptyList<LocalSiegeTask>()
        private val row = LocalTaskAttendanceRow(7, "测试成员", "一团", 100, 20, 1, 1, 0, 1, 0, 30, 1000, "已参战")
        private val battle = LocalTaskBattleRow(99, 1000, 1, "测试成员", "测试盟", 0, "陆逊 / 周瑜 / 吕蒙")
        override fun loadTasks(): List<LocalSiegeTask> = tasks
        override fun loadGroups(): List<String> = listOf("一团")
        override fun create(name: String, pos: String, groups: List<String>, taskTime: Long, targetUids: List<Long>): LocalSiegeTask {
            creates++
            return LocalSiegeTask(1, name, taskTime, 100020, groups.joinToString(), "", 1, 0, 0, 0, 1, 1).also { tasks = listOf(it) }
        }
        override fun task(id: Long): LocalSiegeTask? = tasks.firstOrNull { it.id == id }
        override fun attendance(id: Long): List<LocalTaskAttendanceRow> = listOf(row)
        override fun battles(id: Long): List<LocalTaskBattleRow> = listOf(battle)
        override fun calculate(id: Long): LocalTaskStatisticSummary { calculations++; return LocalTaskStatisticSummary(1, 1, 1, 0) }
        override fun delete(id: Long) { deletes += id; tasks = emptyList() }
    }
}
