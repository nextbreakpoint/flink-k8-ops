package com.nextbreakpoint.flinkoperator.controller.command

import com.nextbreakpoint.flinkoperator.common.model.ClusterId
import com.nextbreakpoint.flinkoperator.common.model.FlinkOptions
import com.nextbreakpoint.flinkoperator.common.model.Result
import com.nextbreakpoint.flinkoperator.common.model.ResultStatus
import com.nextbreakpoint.flinkoperator.common.model.SavepointRequest
import com.nextbreakpoint.flinkoperator.common.utils.FlinkContext
import com.nextbreakpoint.flinkoperator.common.utils.KubernetesContext
import com.nextbreakpoint.flinkoperator.controller.OperatorCommand
import org.apache.log4j.Logger

class SavepointGetStatus(flinkOptions: FlinkOptions, flinkContext: FlinkContext, kubernetesContext: KubernetesContext) : OperatorCommand<SavepointRequest, String>(flinkOptions, flinkContext, kubernetesContext) {
    companion object {
        private val logger = Logger.getLogger(SavepointGetStatus::class.simpleName)
    }

    override fun execute(clusterId: ClusterId, params: SavepointRequest): Result<String> {
        try {
            val address = kubernetesContext.findFlinkAddress(flinkOptions, clusterId.namespace, clusterId.name)

            val requests = mapOf(params.jobId to params.triggerId)

            val pendingSavepointRequests = flinkContext.getPendingSavepointRequests(address, requests)

            if (pendingSavepointRequests.isNotEmpty()) {
                return Result(
                    ResultStatus.AWAIT,
                    ""
                )
            }

            val savepointPaths = flinkContext.getLatestSavepointPaths(address, requests)

            if (savepointPaths.isEmpty()) {
                logger.error("Can't find any savepoint in cluster ${clusterId.name}")

                return Result(
                    ResultStatus.FAILED,
                    ""
                )
            }

            return Result(
                ResultStatus.SUCCESS,
                savepointPaths.values.first()
            )
        } catch (e : Exception) {
            logger.error("Can't get savepoint status of cluster ${clusterId.name}", e)

            return Result(
                ResultStatus.FAILED,
                ""
            )
        }
    }
}