package com.nextbreakpoint.flinkoperator.controller

import com.nextbreakpoint.flinkoperator.common.model.ClusterStatus
import com.nextbreakpoint.flinkoperator.common.model.OperatorTask
import com.nextbreakpoint.flinkoperator.common.model.SavepointRequest
import com.nextbreakpoint.flinkoperator.common.model.TaskStatus
import com.nextbreakpoint.flinkoperator.testing.TestFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OperatorStateTest {
    private val flinkCluster = TestFactory.aCluster(name = "test", namespace = "flink")

    @Test
    fun `initially a cluster doesn't have a current task`() {
        assertThat(OperatorState.hasCurrentTask(flinkCluster)).isFalse()
        assertThat(OperatorState.getCurrentTask(flinkCluster)).isEqualTo(OperatorTask.ClusterHalted)
    }

    @Test
    fun `cluster should have a current task after appending a new task`() {
        OperatorState.appendTasks(flinkCluster, listOf(OperatorTask.InitialiseCluster))
        assertThat(OperatorState.hasCurrentTask(flinkCluster)).isTrue()
    }

    @Test
    fun `should return default task`() {
        assertThat(OperatorState.getCurrentTask(flinkCluster)).isEqualTo(OperatorTask.ClusterHalted)
    }

    @Test
    fun `should remain on default task`() {
        OperatorState.selectNextTask(flinkCluster)
        assertThat(OperatorState.getCurrentTask(flinkCluster)).isEqualTo(OperatorTask.ClusterHalted)
    }

    @Test
    fun `should return current task`() {
        OperatorState.appendTasks(flinkCluster, listOf(OperatorTask.InitialiseCluster))
        OperatorState.appendTasks(flinkCluster, listOf(OperatorTask.StartingCluster))
        assertThat(OperatorState.getCurrentTask(flinkCluster)).isEqualTo(OperatorTask.InitialiseCluster)
    }

    @Test
    fun `should return next task`() {
        OperatorState.appendTasks(flinkCluster, listOf(OperatorTask.InitialiseCluster))
        OperatorState.appendTasks(flinkCluster, listOf(OperatorTask.StartingCluster))
        assertThat(OperatorState.getNextOperatorTask(flinkCluster)).isEqualTo(OperatorTask.StartingCluster)
    }

    @Test
    fun `should advance to next task`() {
        OperatorState.appendTasks(flinkCluster, listOf(OperatorTask.InitialiseCluster))
        OperatorState.appendTasks(flinkCluster, listOf(OperatorTask.StartingCluster))
        OperatorState.selectNextTask(flinkCluster)
        assertThat(OperatorState.getCurrentTask(flinkCluster)).isEqualTo(OperatorTask.StartingCluster)
    }

    @Test
    fun `should remain on last task`() {
        OperatorState.appendTasks(flinkCluster, listOf(OperatorTask.InitialiseCluster))
        OperatorState.appendTasks(flinkCluster, listOf(OperatorTask.StartingCluster))
        OperatorState.selectNextTask(flinkCluster)
        OperatorState.selectNextTask(flinkCluster)
        assertThat(OperatorState.getCurrentTask(flinkCluster)).isEqualTo(OperatorTask.StartingCluster)
    }

    @Test
    fun `should reset tasks`() {
        OperatorState.appendTasks(flinkCluster, listOf(OperatorTask.InitialiseCluster))
        OperatorState.resetTasks(flinkCluster, listOf(OperatorTask.StartingCluster))
        assertThat(OperatorState.getCurrentTask(flinkCluster)).isEqualTo(OperatorTask.StartingCluster)
    }

    @Test
    fun `should update timestamp when appending tasks`() {
        val timestamp = System.currentTimeMillis()
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isEqualTo(0)
        OperatorState.appendTasks(flinkCluster, listOf(OperatorTask.InitialiseCluster))
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
    }

    @Test
    fun `should update timestamp when advancing tasks`() {
        val timestamp = System.currentTimeMillis()
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isEqualTo(0)
        OperatorState.selectNextTask(flinkCluster)
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
    }

    @Test
    fun `should update timestamp when resetting tasks`() {
        val timestamp = System.currentTimeMillis()
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isEqualTo(0)
        OperatorState.resetTasks(flinkCluster, listOf(OperatorTask.InitialiseCluster))
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
    }

    @Test
    fun `should store savepoint path and update timestamp`() {
        val timestamp = System.currentTimeMillis()
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isEqualTo(0)
        assertThat(OperatorState.getSavepointTimestamp(flinkCluster)).isEqualTo(0)
        OperatorState.setSavepointPath(flinkCluster, "/tmp/xxx")
        assertThat(OperatorState.getSavepointPath(flinkCluster)).isEqualTo("/tmp/xxx")
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
        assertThat(OperatorState.getSavepointTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
    }

    @Test
    fun `should store savepoint request and update timestamp`() {
        val timestamp = System.currentTimeMillis()
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isEqualTo(0)
        assertThat(OperatorState.getSavepointTimestamp(flinkCluster)).isEqualTo(0)
        OperatorState.setSavepointRequest(flinkCluster, SavepointRequest("000", "XXX"))
        assertThat(OperatorState.getSavepointRequest(flinkCluster)).isEqualTo(SavepointRequest("000", "XXX"))
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
        assertThat(OperatorState.getSavepointTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
    }

    @Test
    fun `should update savepoint timestamp`() {
        val timestamp = System.currentTimeMillis()
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isEqualTo(0)
        assertThat(OperatorState.getSavepointTimestamp(flinkCluster)).isEqualTo(0)
        OperatorState.updateSavepointTimestamp(flinkCluster)
        assertThat(OperatorState.getSavepointRequest(flinkCluster)).isNull()
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
        assertThat(OperatorState.getSavepointTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
    }

    @Test
    fun `should store operator status and update timestamp`() {
        val timestamp = System.currentTimeMillis()
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isEqualTo(0)
        OperatorState.setTaskStatus(flinkCluster, TaskStatus.Awaiting)
        assertThat(OperatorState.getCurrentTaskStatus(flinkCluster)).isEqualTo(TaskStatus.Awaiting)
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
    }

    @Test
    fun `should store cluster status and update timestamp`() {
        val timestamp = System.currentTimeMillis()
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isEqualTo(0)
        OperatorState.setClusterStatus(flinkCluster, ClusterStatus.Checkpointing)
        assertThat(OperatorState.getClusterStatus(flinkCluster)).isEqualTo(ClusterStatus.Checkpointing)
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
    }

    @Test
    fun `should store job manager digest and update timestamp`() {
        val timestamp = System.currentTimeMillis()
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isEqualTo(0)
        OperatorState.setJobManagerDigest(flinkCluster, "XXX")
        assertThat(OperatorState.getJobManagerDigest(flinkCluster)).isEqualTo("XXX")
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
    }

    @Test
    fun `should store task manager digest and update timestamp`() {
        val timestamp = System.currentTimeMillis()
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isEqualTo(0)
        OperatorState.setTaskManagerDigest(flinkCluster, "XXX")
        assertThat(OperatorState.getTaskManagerDigest(flinkCluster)).isEqualTo("XXX")
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
    }

    @Test
    fun `should store flink image digest and update timestamp`() {
        val timestamp = System.currentTimeMillis()
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isEqualTo(0)
        OperatorState.setRuntimeDigest(flinkCluster, "XXX")
        assertThat(OperatorState.getRuntimeDigest(flinkCluster)).isEqualTo("XXX")
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
    }

    @Test
    fun `should store flink job digest and update timestamp`() {
        val timestamp = System.currentTimeMillis()
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isEqualTo(0)
        OperatorState.setBootstrapDigest(flinkCluster, "XXX")
        assertThat(OperatorState.getBootstrapDigest(flinkCluster)).isEqualTo("XXX")
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
    }

    @Test
    fun `should store task attempts and update timestamp`() {
        val timestamp = System.currentTimeMillis()
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isEqualTo(0)
        OperatorState.setTaskAttempts(flinkCluster, 2)
        assertThat(OperatorState.getTaskAttempts(flinkCluster)).isEqualTo(2)
        assertThat(OperatorState.getOperatorTimestamp(flinkCluster)).isGreaterThanOrEqualTo(timestamp)
    }
}
