package com.example.ui.screens.chat

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.example.R
import com.example.Res

object LocalChatStorage {
    private const val FILE_NAME = "chat_history.json"

    suspend fun saveChatHistory(context: Context, peers: Set<DevicePeer>, messages: List<ChatMessage>) { // EXT-R13
        withContext(Dispatchers.IO) {
            try {
                val rootObj = JSONObject()

                // Save peers
                val peersArray = JSONArray()
                for (peer in peers) {
                    val peerObj = JSONObject().apply {
                        put("name", peer.name)
                        put("ip", peer.ip)
                        put("lastSeen", peer.lastSeen)
                    }
                    peersArray.put(peerObj)
                }
                rootObj.put("peers", peersArray)

                // Save messages
                val messagesArray = JSONArray()
                for (msg in messages) {
                    val msgObj = JSONObject().apply {
                        put("id", msg.id)
                        put("sender", msg.sender)
                        put("content", msg.content)
                        put("timestamp", msg.timestamp)
                        put("isMe", msg.isMe)
                        put("isFileType", msg.isFileType)
                        put("fileName", msg.fileName)
                        put("fileUriStr", msg.fileUriStr)
                        put("downloadUrl", msg.downloadUrl)
                        put("fileSize", msg.fileSize)
                        put("peerIp", msg.peerIp)
                    }
                    messagesArray.put(msgObj)
                }
                rootObj.put("messages", messagesArray)

                val file = File(context.filesDir, FILE_NAME)
                file.writeText(rootObj.toString())
            } catch (e: Exception) {
                Log.e("LocalChatStorage", "Failed to save chat history", e)
            }
        }
    }

    suspend fun loadChatHistory(context: Context): Pair<Set<DevicePeer>, List<ChatMessage>> {
        return withContext(Dispatchers.IO) {
            val peers = mutableSetOf<DevicePeer>()
            val messages = mutableListOf<ChatMessage>()
            
            try {
                val file = File(context.filesDir, FILE_NAME)
                if (file.exists()) {
                    val content = file.readText()
                    val rootObj = JSONObject(content)
                    
                    if (rootObj.has("peers")) {
                        val peersArray = rootObj.getJSONArray("peers")
                        for (i in 0 until peersArray.length()) {
                            val peerObj = peersArray.getJSONObject(i)
                            peers.add(
                                DevicePeer(
                                    name = peerObj.optString("name", Res.get(R.string.desconhecido)),
                                    ip = peerObj.optString("ip", ""),
                                    lastSeen = peerObj.optLong("lastSeen", 0L)
                                )
                            )
                        }
                    }

                    if (rootObj.has("messages")) {
                        val messagesArray = rootObj.getJSONArray("messages")
                        for (i in 0 until messagesArray.length()) {
                            val msgObj = messagesArray.getJSONObject(i)
                            val rawIsFile = msgObj.optBoolean("isFileType", false)
                            val contentStr = msgObj.optString("content", "")
                            var fileNameStr = msgObj.optString("fileName", "")
                            val isFile = rawIsFile || contentStr.contains(Res.get(R.string.recebeu)) || contentStr.contains(Res.get(R.string.enviou))
                            if (fileNameStr.isBlank() && isFile) {
                                fileNameStr = when {
                                    contentStr.contains(Res.get(R.string.recebeu)) -> contentStr.substringAfter(Res.get(R.string.recebeu)).substringBeforeLast(" (").trim()
                                    contentStr.contains(Res.get(R.string.enviou)) -> contentStr.substringAfter(Res.get(R.string.enviou)).substringBeforeLast(" (").trim()
                                    else -> ""
                                }
                            }

                            messages.add(
                                ChatMessage(
                                    id = msgObj.optString("id", java.util.UUID.randomUUID().toString()),
                                    sender = msgObj.optString("sender", Res.get(R.string.desconhecido)),
                                    content = contentStr,
                                    timestamp = msgObj.optLong("timestamp", 0L),
                                    isMe = msgObj.optBoolean("isMe", false),
                                    isFileType = isFile,
                                    fileName = fileNameStr,
                                    fileUriStr = msgObj.optString("fileUriStr", ""),
                                    downloadUrl = msgObj.optString("downloadUrl", ""),
                                    fileSize = msgObj.optLong("fileSize", 0L),
                                    peerIp = msgObj.optString("peerIp", "")
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LocalChatStorage", "Failed to load chat history", e)
            }
            Pair(peers, messages)
        }
    }
}
