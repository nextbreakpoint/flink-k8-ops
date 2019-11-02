package com.nextbreakpoint.flinkoperator.controller

import com.nextbreakpoint.flinkoperator.common.model.ClusterId
import com.nextbreakpoint.flinkoperator.common.model.FlinkOptions
import com.nextbreakpoint.flinkoperator.common.model.Result
import com.nextbreakpoint.flinkoperator.common.utils.FlinkContext
import com.nextbreakpoint.flinkoperator.common.utils.KubernetesContext

abstract class OperatorCommand<T, R>(val flinkOptions : FlinkOptions, val flinkContext: FlinkContext, val kubernetesContext: KubernetesContext) {
    abstract fun execute(clusterId: ClusterId, params: T): Result<R>
}