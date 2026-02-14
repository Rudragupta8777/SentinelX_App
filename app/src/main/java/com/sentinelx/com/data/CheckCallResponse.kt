package com.sentinelx.com.data

data class CheckCallResponse(
    val action: String,
    val riskScore: Int,
    val uiMessage: String,
    val traceId: String
)
