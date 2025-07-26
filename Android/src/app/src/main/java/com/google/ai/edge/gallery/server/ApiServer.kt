package com.google.ai.edge.gallery.server

import com.google.ai.edge.gallery.data.TASK_LLM_SERVER
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.QueueDispatcher
import mockwebserver3.RecordedRequest
import java.net.InetAddress

object ApiServer {

    fun startServer(scope: CoroutineScope, worker: (String, List<Message>) -> String) {
        scope.launch(Dispatchers.IO) {
            val server = MockWebServer()
            server.dispatcher = LLMDispatcher(worker)
            server.start(inetAddress = InetAddress.getByName("0.0.0.0"), port = 8080)
        }
    }

    private class LLMDispatcher(private val worker: (String, List<Message>) -> String) :
        QueueDispatcher() {

        private val gson = Gson()

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.url.toUrl().path
            val any = if (path.equals("/v1/chat/completions")) {
                val body = request.body?.string(Charsets.UTF_8)!!
                val param = parseRequest<ChatRequest>(body)
                worker(param.model, param.messages)
            } else if (path.equals("/v1/models")) {
                gson.toJson(TASK_LLM_SERVER.models.map { it.name })
            } else {
                ""
            }
            return MockResponse(body = any)
        }

        private inline fun <reified T> parseRequest(json: String): T {
            return gson.fromJson<T>(json, T::class.java)
        }

    }

}