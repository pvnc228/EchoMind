package com.echomind.data.remote

import com.echomind.data.remote.dto.AnalysisRequest
import com.echomind.data.remote.dto.AnalysisResponse
import com.echomind.data.remote.dto.TranscriptionResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

const val QUESTION_API_PATH = "v1/chat/completions"
const val APPROVED_DESTINATION_HEADER = "X-EchoMind-Approved-Destination"

interface LlmApi {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Part audio: MultipartBody.Part,
        @Part("model") model: okhttp3.RequestBody,
        @Part("response_format") responseFormat: okhttp3.RequestBody
    ): TranscriptionResponse

    @POST(QUESTION_API_PATH)
    suspend fun analyzeText(
        @Url url: String,
        @Header(APPROVED_DESTINATION_HEADER) approvedDestination: String,
        @Body request: AnalysisRequest
    ): AnalysisResponse
}
