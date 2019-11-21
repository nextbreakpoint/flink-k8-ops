package com.nextbreakpoint.flinkoperator.common.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nextbreakpoint.flinkclient.api.FlinkApi
import com.nextbreakpoint.flinkclient.model.AsynchronousOperationResult
import com.nextbreakpoint.flinkclient.model.CheckpointingStatistics
import com.nextbreakpoint.flinkclient.model.ClusterOverviewWithVersion
import com.nextbreakpoint.flinkclient.model.JarFileInfo
import com.nextbreakpoint.flinkclient.model.JarListInfo
import com.nextbreakpoint.flinkclient.model.JarUploadResponseBody
import com.nextbreakpoint.flinkclient.model.JobDetailsInfo
import com.nextbreakpoint.flinkclient.model.JobIdWithStatus
import com.nextbreakpoint.flinkclient.model.JobIdsWithStatusOverview
import com.nextbreakpoint.flinkclient.model.QueueStatus
import com.nextbreakpoint.flinkclient.model.SavepointTriggerRequestBody
import com.nextbreakpoint.flinkclient.model.TaskManagerDetailsInfo
import com.nextbreakpoint.flinkclient.model.TaskManagersInfo
import com.nextbreakpoint.flinkclient.model.TriggerResponse
import com.nextbreakpoint.flinkoperator.common.crd.V1BootstrapSpec
import com.nextbreakpoint.flinkoperator.common.model.FlinkAddress
import com.nextbreakpoint.flinkoperator.common.model.Metric
import com.nextbreakpoint.flinkoperator.common.model.TaskManagerId
import org.apache.log4j.Logger
import java.io.File
import java.util.concurrent.TimeUnit

object FlinkClient {
    private val logger = Logger.getLogger(FlinkClient::class.simpleName)

    private const val TIMEOUT = 20000L

    fun getOverview(address: FlinkAddress): ClusterOverviewWithVersion {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            val response = flinkApi.getOverviewCall(null, null).execute()

            response.body().use { body ->
                if (!response.isSuccessful) {
                    throw CallException("Can't get cluster overview - $address")
                }

                body.source().use { source ->
                    return Gson().fromJson(source.readUtf8Line(), ClusterOverviewWithVersion::class.java)
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun listJars(address: FlinkAddress): List<JarFileInfo> {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            val response = flinkApi.listJarsCall(null, null).execute()

            response.body().use { body ->
                if (!response.isSuccessful) {
                    throw CallException("Can't list JARs - $address")
                }

                body.source().use { source ->
                    return Gson().fromJson(source.readUtf8Line(), JarListInfo::class.java).files
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun deleteJars(address: FlinkAddress, files: List<JarFileInfo>) {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            files.forEach {
                val response = flinkApi.deleteJarCall(it.id, null, null).execute()

                response.body().use { body ->
                    if (!response.isSuccessful) {
                        throw CallException("Can't remove JAR - $address")
                    }
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun listRunningJobs(address: FlinkAddress): List<String> {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            val response = flinkApi.getJobsCall( null, null).execute()

            response.body().use { body ->
                if (!response.isSuccessful) {
                    throw CallException("Can't get jobs - $address")
                }

                body.source().use { source ->
                    val jobsOverview = Gson().fromJson(source.readUtf8Line(), JobIdsWithStatusOverview::class.java)

                    return jobsOverview.jobs.filter {
                        jobIdWithStatus -> jobIdWithStatus.status == JobIdWithStatus.StatusEnum.RUNNING
                    }.map {
                        it.id
                    }.toList()
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun listJobs(address: FlinkAddress): List<JobIdWithStatus> {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            val response = flinkApi.getJobsCall( null, null).execute()

            response.body().use { body ->
                if (!response.isSuccessful) {
                    throw CallException("Can't get jobs - $address")
                }

                body.source().use { source ->
                    val jobsOverview = Gson().fromJson(source.readUtf8Line(), JobIdsWithStatusOverview::class.java)

                    return jobsOverview.jobs
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun runJar(address: FlinkAddress, jarFile: JarFileInfo, bootstrap: V1BootstrapSpec, parallelism: Int, savepointPath: String?) {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            val response = flinkApi.runJarCall(
                jarFile.id,
                false,
                savepointPath,
                bootstrap.arguments.joinToString(separator = " "),
                null,
                bootstrap.className,
                parallelism,
                null,
                null
            ).execute()

            response.body().use { body ->
                if (!response.isSuccessful) {
                    throw CallException("Can't run JAR - $address")
                }

                body.source().use { source ->
                    logger.debug("Job started: ${source.readUtf8Line()}")
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun getCheckpointingStatistics(address: FlinkAddress, jobs: List<String>): Map<String, CheckpointingStatistics> {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            return jobs.map { jobId ->
                val response = flinkApi.getJobCheckpointsCall(jobId, null, null).execute()

                jobId to response
            }.map {
                it.second.body().use { body ->
                    if (!it.second.isSuccessful) {
                        throw CallException("Can't get checkpointing statistics - $address")
                    }

                    it.first to body.source().use { source ->
                        Gson().fromJson(source.readUtf8Line(), CheckpointingStatistics::class.java)
                    }
                }
            }.toMap()
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun createSavepoint(address: FlinkAddress, it: String, targetPath: String?): TriggerResponse {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            val requestBody = SavepointTriggerRequestBody().cancelJob(true).targetDirectory(targetPath)

            val response = flinkApi.createJobSavepointCall(requestBody, it, null, null).execute()

            response.body().use { body ->
                if (!response.isSuccessful) {
                    throw CallException("Can't request savepoint - $address")
                }

                body.source().use { source ->
                    return Gson().fromJson(source.readUtf8Line(), TriggerResponse::class.java)
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun getJobDetails(address: FlinkAddress, jobId: String): JobDetailsInfo {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            val response = flinkApi.getJobDetailsCall(jobId, null, null).execute()

            response.body().use { body ->
                if (!response.isSuccessful) {
                    throw CallException("Can't fetch job details - $address")
                }

                body.source().use { source ->
                    return Gson().fromJson(source.readUtf8Line(), JobDetailsInfo::class.java)
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun getJobMetrics(address: FlinkAddress, jobId: String, metricKey: String): List<Metric> {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            val response = flinkApi.getJobMetricsCall(jobId, metricKey, null, null).execute()

            response.body().use { body ->
                if (!response.isSuccessful) {
                    throw CallException("Can't fetch job metrics - $address")
                }

                body.source().use { source ->
                    return Gson().fromJson(source.readUtf8Line(), object : TypeToken<List<Metric>>() {}.type)
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun getJobManagerMetrics(address: FlinkAddress, metricKey: String): List<Metric> {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            val response = flinkApi.getJobManagerMetricsCall(metricKey, null, null).execute()

            response.body().use { body ->
                if (!response.isSuccessful) {
                    throw CallException("Can't fetch job manager metrics - $address")
                }

                body.source().use { source ->
                    return Gson().fromJson(source.readUtf8Line(), object : TypeToken<List<Metric>>() {}.type)
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun getTaskManagerMetrics(address: FlinkAddress, taskmanagerId: TaskManagerId, metricKey: String): List<Metric> {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            val response = flinkApi.getTaskManagerMetricsCall(taskmanagerId.taskmanagerId, metricKey, null, null).execute()

            response.body().use { body ->
                if (!response.isSuccessful) {
                    throw CallException("Can't fetch task manager metrics - $address")
                }

                body.source().use { source ->
                    return Gson().fromJson(source.readUtf8Line(), object : TypeToken<List<Metric>>() {}.type)
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun getTaskManagerDetails(address: FlinkAddress, taskmanagerId: TaskManagerId): TaskManagerDetailsInfo {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            val response = flinkApi.getTaskManagerDetailsCall(taskmanagerId.taskmanagerId, null, null).execute()

            response.body().use { body ->
                if (!response.isSuccessful) {
                    throw CallException("Can't fetch task manager details - $address")
                }

                body.source().use { source ->
                    return Gson().fromJson(source.readUtf8Line(), object : TypeToken<TaskManagerDetailsInfo>() {}.type)
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun terminateJobs(address: FlinkAddress, jobs: List<String>) {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            jobs.forEach {
                val response = flinkApi.terminateJobCall(it, "cancel", null, null).execute()

                response.body().use { body ->
                    if (!response.isSuccessful) {
                        logger.warn("Can't cancel job $it - $address");
                    }
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun getTaskManagersOverview(address: FlinkAddress): TaskManagersInfo {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            val response = flinkApi.getTaskManagersOverviewCall(null, null).execute()

            response.body().use { body ->
                if (!response.isSuccessful) {
                    throw CallException("Can't fetch task managers overview - $address")
                }

                body.source().use { source ->
                    return Gson().fromJson(source.readUtf8Line(), object : TypeToken<TaskManagersInfo>() {}.type)
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun getPendingSavepointRequests(address: FlinkAddress, requests: Map<String, String>): List<String> {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            return requests.map { (jobId, requestId) ->
                val response = flinkApi.getJobSavepointStatusCall(jobId, requestId, null, null).execute()

                jobId to response
            }.map {
                it.second.body().use { body ->
                    if (!it.second.isSuccessful) {
                        logger.error("Can't get savepoint status for job ${it.first} - $address")
                    }

                    val asynchronousOperationResult = body.source().use { source ->
                        Gson().fromJson(source.readUtf8Line(), AsynchronousOperationResult::class.java)
                    }

                    if (asynchronousOperationResult.status.id != QueueStatus.IdEnum.COMPLETED) {
                        logger.info("Savepoint still in progress for job ${it.first} - $address")
                    }

                    it.first to asynchronousOperationResult.status.id
                }
            }.filter {
                it.second == QueueStatus.IdEnum.IN_PROGRESS
            }.map {
                it.first
            }.toList()
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun getLatestSavepointPaths(address: FlinkAddress, requests: Map<String, String>): Map<String, String> {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            return requests.map { (jobId, _) ->
                val response = flinkApi.getJobCheckpointsCall(jobId, null, null).execute()

                jobId to response
            }.map {
                it.second.body().use { body ->
                    if (!it.second.isSuccessful) {
                        logger.error("Can't get checkpointing statistics for job ${it.first} - $address")
                    }

                    val checkpointingStatistics = body.source().use { source ->
                        Gson().fromJson(source.readUtf8Line(), CheckpointingStatistics::class.java)
                    }

                    val savepoint = checkpointingStatistics.latest?.savepoint

                    if (savepoint == null) {
                        logger.error("Savepoint not found for job ${it.first} - $address")
                    }

                    val externalPathOrEmpty = savepoint?.externalPath ?: ""

                    it.first to externalPathOrEmpty.trim('\"')
                }
            }.filter {
                it.second.isNotBlank()
            }.toMap()
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun triggerSavepoints(address: FlinkAddress, jobs: List<String>, targetPath: String?): Map<String, String>  {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            return jobs.map {
                val requestBody = SavepointTriggerRequestBody().cancelJob(false).targetDirectory(targetPath)

                val response = flinkApi.createJobSavepointCall(requestBody, it, null, null).execute()

                it to response
            }.map {
                it.second.body().use { body ->
                    if (!it.second.isSuccessful) {
                        logger.warn("Can't request savepoint for job $it - $address")
                    }

                    it.first to body.source().use { source ->
                        Gson().fromJson(source.readUtf8Line(), TriggerResponse::class.java)
                    }
                }
            }.map {
                it.first to it.second.requestId
            }.onEach {
                logger.info("Created savepoint request ${it.second} for job ${it.first} - $address")
            }.toMap()
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun uploadJarCall(address: FlinkAddress, file: File): JarUploadResponseBody {
        try {
            val flinkApi = createFlinkApiClient(address, TIMEOUT)

            val response = flinkApi.uploadJarCall(file, null, null).execute();

            response.body().use { body ->
                if (!response.isSuccessful) {
                    throw CallException("Can't upload JAR - $address")
                }

                body.source().use { source ->
                    return Gson().fromJson(source.readUtf8Line(), object : TypeToken<JarUploadResponseBody>() {}.type)
                }
            }
        } catch (e : CallException) {
            throw e
        } catch (e : Exception) {
            throw RuntimeException(e)
        }
    }

    fun triggerJobRescaling(address: FlinkAddress, parallelism: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun createFlinkApiClient(address: FlinkAddress, timeout: Long): FlinkApi {
        val flinkApi = FlinkApi()
        val apiClient = flinkApi.apiClient
        apiClient.basePath = "http://${address.host}:${address.port}"
        apiClient.httpClient.setConnectTimeout(timeout, TimeUnit.MILLISECONDS)
        apiClient.httpClient.setWriteTimeout(timeout, TimeUnit.MILLISECONDS)
        apiClient.httpClient.setReadTimeout(timeout, TimeUnit.MILLISECONDS)
        apiClient.isDebugging = System.getProperty("flink.client.debugging", "false")!!.toBoolean()
        return flinkApi
    }
}