package com.example.service

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class MailtrapEmail(
    val from: EmailAddress,
    val to: List<EmailAddress>,
    val subject: String,
    val text: String? = null,
    val html: String? = null,
    val category: String? = null
)

@JsonClass(generateAdapter = true)
data class EmailAddress(
    val email: String,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class MailtrapResponse(
    val success: Boolean,
    val message_ids: List<String>? = null,
    val error: String? = null
)

class MailtrapEmailService(
    private val apiToken: String,
    private val apiUrl: String = "https://send.api.mailtrap.io/api/send" // COM-R07
) {
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("Authorization", "Bearer $apiToken")
                .header("Content-Type", "application/json")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        .build()

    private val json = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    private val emailAdapter = json.adapter(MailtrapEmail::class.java)
    private val responseAdapter = json.adapter(MailtrapResponse::class.java)

    suspend fun sendEmail(email: MailtrapEmail): Result<MailtrapResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = emailAdapter.toJson(email)
            val body = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(apiUrl)
                .post(body)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val mailtrapResponse = if (responseBody != null) {
                    responseAdapter.fromJson(responseBody) ?: MailtrapResponse(true, null, null)
                } else {
                    MailtrapResponse(true, null, null)
                }
                Result.success(mailtrapResponse)
            } else {
                val errorBody = response.body?.string()
                Result.failure(Exception("HTTP ${response.code}: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendSimpleEmail(
        fromEmail: String,
        fromName: String? = null,
        toEmail: String,
        toName: String? = null,
        subject: String,
        text: String? = null,
        html: String? = null,
        category: String? = null
    ): Result<MailtrapResponse> {
        val email = MailtrapEmail(
            from = EmailAddress(fromEmail, fromName),
            to = listOf(EmailAddress(toEmail, toName)),
            subject = subject,
            text = text,
            html = html,
            category = category
        )
        return sendEmail(email)
    }
}
