package com.google.ai.edge.gallery.server

data class ChatRequest(val model: String, val messages: List<Message>, val max_tokens: Int)

data class Message(val role: String, val content: List<Content>)

sealed class Content {
    data class TextContent(val text: String) : Content()
    data class ImageUrlContent(val image_url: ImageUrl) : Content()
}

data class ImageUrl(val url: String)