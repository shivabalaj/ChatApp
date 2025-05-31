package com.example.samplesocket

data class ChatMessage(
    val sender: String,
    val message: String,
    val timestamp: String,
    val isOwn: Boolean,
    var isDelivered: Boolean = false
)