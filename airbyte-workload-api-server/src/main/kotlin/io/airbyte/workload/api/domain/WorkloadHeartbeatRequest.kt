package io.airbyte.workload.api.domain

import io.swagger.v3.oas.annotations.media.Schema

class WorkloadHeartbeatRequest(
  @Schema(required = true)
  var workloadId: String = "",
)
