package com.echomind.data.remote

import com.echomind.data.remote.dto.AnalysisRequest
import com.echomind.data.remote.dto.AnalysisResponse
import com.echomind.data.remote.dto.TranscriptionResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface LlmApi {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Part audio: MultipartBody.Part,
        @Part("model") model: okhttp3.RequestBody,
        @Part("response_format") responseFormat: okhttp3.RequestBody
    ): TranscriptionResponse

    @POST("v1/chat/completions")
    suspend fun analyzeText(
        @Body request: AnalysisRequest
    ): AnalysisResponse
}
