package com.sentinelx.com.network

import com.sentinelx.com.data.CheckCallRequest
import com.sentinelx.com.data.CheckCallResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST



interface SentinelApiService {
    @POST("call/check")
    suspend fun checkCall(
        @Header("X-Correlation-ID") correlationId: String,
        @Body request: CheckCallRequest
    ): CheckCallResponse
}
