package com.sentinelx.com.data

data class ReportRequest(
    val reporter: String,
    val scammer: String,
    val reason: String
)