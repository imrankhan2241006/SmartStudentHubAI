package com.imran.smartstudenthub

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiService {

    private val models = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.8-flash"
    )

    fun generate(prompt: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isBlank()) {
            return "API Key missing. Please check local.properties."
        }

        return try {
            var lastError = ""

            for (model in models) {
                val (code, responseText) = sendRequest(
                    model,
                    prompt,
                    apiKey
                )

                if (code in 200..299) {
                    return parseResponse(responseText)
                }

                val message = try {
                    JSONObject(responseText)
                        .getJSONObject("error")
                        .optString("message", responseText)
                } catch (_: Exception) {
                    responseText
                }

                lastError = "HTTP $code: $message"

                if (code != 503 && code != 429) {
                    return "Gemini Error: $lastError"
                }
            }

            "Both Gemini models are currently busy. Please try again shortly. Details: $lastError"

        } catch (e: Exception) {
            "Connection Error: ${e.localizedMessage ?: "Please check your internet connection."}"
        }
    }

    private fun sendRequest(
        model: String,
        prompt: String,
        apiKey: String
    ): Pair<Int, String> {

        var connection: HttpURLConnection? = null

        try {
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            )

            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "x-goog-api-key",
                apiKey
            )

            val part = JSONObject().put("text", prompt)
            val parts = JSONArray().put(part)
            val content = JSONObject().put("parts", parts)
            val contents = JSONArray().put(content)

            val requestBody = JSONObject()
                .put("contents", contents)

            connection.outputStream.use {
                it.write(
                    requestBody.toString().toByteArray(Charsets.UTF_8)
                )
            }

            val responseCode = connection.responseCode

            val responseStream =
                if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val responseText = responseStream
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: ""

            return Pair(responseCode, responseText)

        } finally {
            connection?.disconnect()
        }
    }

    private fun parseResponse(responseText: String): String {
        val json = JSONObject(responseText)

        val candidates = json.optJSONArray("candidates")
            ?: return "AI did not return a response. Please try again."

        if (candidates.length() == 0) {
            return "No AI response available. Please try again."
        }

        val responseParts = candidates
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")

        val answer = StringBuilder()

        for (i in 0 until responseParts.length()) {
            answer.append(
                responseParts.getJSONObject(i).optString("text", "")
            )
        }

        return answer.toString().ifBlank {
            "AI returned an empty response. Please try again."
        }
    }
}