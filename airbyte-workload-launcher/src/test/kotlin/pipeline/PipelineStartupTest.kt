/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workload.launcher.pipeline

import io.airbyte.workload.api.client2.generated.WorkloadApi
import io.airbyte.workload.api.client2.model.generated.Workload
import io.airbyte.workload.api.client2.model.generated.WorkloadListRequest
import io.airbyte.workload.api.client2.model.generated.WorkloadListResponse
import io.airbyte.workload.api.client2.model.generated.WorkloadStatus
import io.airbyte.workload.launcher.StartupApplicationEventListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.temporal.worker.WorkerFactory
import org.junit.jupiter.api.Test
import pipeline.SharedMocks.Companion.metricPublisher

class PipelineStartupTest {
  @Test
  fun `should process claimed workloads`() {
    val workloadId = "1"
    val dataplaneId = "US"
    val payload = "payload"
    val logPath = "/log/path"
    val launcherInput = LauncherInput(workloadId, payload, mapOf(), logPath)

    val workloadApiClient: WorkloadApi = mockk()
    val workerFactory: WorkerFactory = mockk()
    val launchPipeline: LaunchPipeline = mockk()

    every {
      launchPipeline.accept(launcherInput)
    } returns Unit

    val workloadListRequest =
      WorkloadListRequest(
        listOf(dataplaneId),
        listOf(WorkloadStatus.cLAIMED),
      )

    val workload =
      Workload(workloadId, listOf(), payload, logPath, dataplaneId, WorkloadStatus.cLAIMED)

    val workloadListResponse = WorkloadListResponse(listOf(workload))

    every {
      workloadApiClient.workloadList(
        workloadListRequest,
      )
    } returns workloadListResponse

    val startupApplicationEventListener =
      spyk(
        StartupApplicationEventListener(
          workloadApiClient,
          launchPipeline,
          workerFactory,
          dataplaneId,
          metricPublisher,
        ),
      )

    startupApplicationEventListener.retrieveAndProcessClaimed()

    verify { workloadApiClient.workloadList(workloadListRequest) }
    verify { launchPipeline.accept(launcherInput) }
  }
}
