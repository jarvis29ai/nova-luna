package com.nova.luna.communication

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class CommunicationGmailReader(private val context: Context) {
    private val authenticator = CommunicationGmailAuthenticator(context)

    fun readTodayMessages(): List<CommunicationMessage> {
        return fetchMessages(null)
    }

    fun searchMessages(query: String): List<CommunicationMessage> {
        return fetchMessages(query)
    }

    private fun fetchMessages(searchQuery: String?): List<CommunicationMessage> {
        val account = authenticator.getGoogleAccount() ?: return emptyList()
        val token = authenticator.getAuthToken(account) ?: return emptyList()

        val messages = mutableListOf<CommunicationMessage>()
        try {
            // Step 1: Get message list
            // We use 'me' to refer to the authenticated user.
            val q = searchQuery?.let { "&q=$it" } ?: ""
            val listUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=25$q"
            val listResponse = makeGetRequest(listUrl, token) ?: return emptyList()
            
            val jsonResponse = JSONObject(listResponse)
            val messagesArray = jsonResponse.optJSONArray("messages") ?: return emptyList()

            for (i in 0 until messagesArray.length()) {
                val messageRef = messagesArray.getJSONObject(i)
                val messageId = messageRef.getString("id")
                
                // Step 2: Get message details (snippet and headers)
                val detailUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/$messageId?format=full"
                val detailResponse = makeGetRequest(detailUrl, token)
                if (detailResponse != null) {
                    val msgJson = JSONObject(detailResponse)
                    messages.add(mapToCommunicationMessage(msgJson))
                }
            }
        } catch (e: Exception) {
            // Safely ignore failures in this local integration
        }
        return messages
    }

    private fun makeGetRequest(urlPath: String, token: String): String? {
        return try {
            val url = URL(urlPath)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                response.toString()
            } else {
                if (responseCode == 401) {
                    authenticator.invalidateToken(token)
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun mapToCommunicationMessage(json: JSONObject): CommunicationMessage {
        val id = json.getString("id")
        val internalDate = json.getLong("internalDate")
        val snippet = json.optString("snippet", "")
        val payload = json.optJSONObject("payload")
        val headers = payload?.optJSONArray("headers")
        
        var sender = "Unknown"
        var subject = ""
        
        if (headers != null) {
            for (i in 0 until headers.length()) {
                val header = headers.getJSONObject(i)
                when (header.getString("name").lowercase()) {
                    "from" -> sender = header.getString("value")
                    "subject" -> subject = header.getString("value")
                }
            }
        }

        val timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(internalDate), ZoneId.systemDefault())

        return CommunicationMessage(
            id = id,
            platform = CommunicationPlatform.GMAIL,
            sender = CommunicationSender(sender),
            timestamp = timestamp,
            content = snippet,
            subject = subject
        )
    }
}
