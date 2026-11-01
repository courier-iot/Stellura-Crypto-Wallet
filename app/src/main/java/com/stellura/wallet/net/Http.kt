package com.stellura.wallet.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun getJson(client: OkHttpClient, url: String): JsonElement = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", "Stellura/0.1").build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("HTTP ${res.code} from ${req.url.host}")
            json.parseToJsonElement(body)
        }
    }

    suspend fun postJson(client: OkHttpClient, url: String, payload: String): JsonElement = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).post(payload.toRequestBody(JSON_TYPE))
            .header("Accept", "application/json").build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("HTTP ${res.code} from ${req.url.host}")
            json.parseToJsonElement(body)
        }
    }
}
