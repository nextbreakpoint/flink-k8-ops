package com.nextbreakpoint.flinkoperator.controller.operation

import com.nextbreakpoint.flinkoperator.common.model.ClusterSelector
import com.nextbreakpoint.flinkoperator.common.model.FlinkOptions
import com.nextbreakpoint.flinkoperator.common.model.SavepointOptions
import com.nextbreakpoint.flinkoperator.common.model.SavepointRequest
import com.nextbreakpoint.flinkoperator.common.utils.FlinkClient
import com.nextbreakpoint.flinkoperator.common.utils.KubeClient
import com.nextbreakpoint.flinkoperator.controller.core.Operation
import com.nextbreakpoint.flinkoperator.controller.core.OperationResult
import com.nextbreakpoint.flinkoperator.controller.core.OperationStatus
import org.apache.log4j.Logger

class SavepointTrigger(flinkOptions: FlinkOptions, flinkClient: FlinkClient, kubeClient: KubeClient) : Operation<SavepointOptions, SavepointRequest?>(flinkOptions, flinkClient, kubeClient) {
    companion object {
        private val logger = Logger.getLogger(SavepointTrigger::class.simpleName)
    }

    override fun execute(clusterSelector: ClusterSelector, params: SavepointOptions): OperationResult<SavepointRequest?> {
        try {
            val address = kubeClient.findFlinkAddress(flinkOptions, clusterSelector.namespace, clusterSelector.name)

            val runningJobs = flinkClient.listRunningJobs(address)

            if (runningJobs.isEmpty()) {
                logger.warn("[name=${clusterSelector.name}] Can't find a running job")

                return OperationResult(
                    OperationStatus.ERROR,
                    null
                )
            }

            if (runningJobs.size > 1) {
                logger.warn("[name=${clusterSelector.name}] There are multiple jobs running")

                return OperationResult(
                    OperationStatus.ERROR,
                    null
                )
            }

            val savepointRequests = flinkClient.triggerSavepoints(address, runningJobs, params.targetPath)

            return OperationResult(
                OperationStatus.OK,
                savepointRequests.map {
                    SavepointRequest(
                        jobId = it.key,
                        triggerId = it.value
                    )
                }.first()
            )
        } catch (e : Exception) {
            logger.error("[name=${clusterSelector.name}] Can't trigger savepoint for job", e)

            return OperationResult(
                OperationStatus.ERROR,
                null
            )
        }
    }
}