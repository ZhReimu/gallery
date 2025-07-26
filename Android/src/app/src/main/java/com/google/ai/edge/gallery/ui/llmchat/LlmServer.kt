package com.google.ai.edge.gallery.ui.llmchat

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.server.ApiServer
import com.google.ai.edge.gallery.server.Content
import com.google.ai.edge.gallery.ui.common.ModelPageAppBar
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object LlmServer {

    private const val TAG = "LlmServerTag"

    @Composable
    fun LlmServer(
        modelManagerViewModel: ModelManagerViewModel,
        navigateUp: () -> Unit,
        modifier: Modifier = Modifier,
        viewModel: LlmServerViewModel,
    ) {

        ServerView(
            task = viewModel.task,
            viewModel = viewModel,
            modelManagerViewModel = modelManagerViewModel,
            onResetSessionClicked = { model -> viewModel.resetSession(model = model) },
            navigateUp = navigateUp,
            modifier = modifier,
        )
    }

    @Composable
    fun ServerView(
        task: Task,
        viewModel: LlmServerViewModel,
        modelManagerViewModel: ModelManagerViewModel,
        navigateUp: () -> Unit,
        modifier: Modifier = Modifier,
        onResetSessionClicked: (Model) -> Unit = {},
    ) {
        val uiState by viewModel.uiState.collectAsState()
        val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
        val selectedModel = modelManagerUiState.selectedModel

        val pagerState = rememberPagerState(
            initialPage = task.models.indexOf(selectedModel),
            pageCount = { task.models.size },
        )
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var navigatingUp by remember { mutableStateOf(false) }

        val handleNavigateUp = {
            navigatingUp = true
            navigateUp()

            // clean up all models.
            scope.launch(Dispatchers.Default) {
                for (model in task.models) {
                    modelManagerViewModel.cleanupModel(task = task, model = model)
                }
            }
        }

        // Initialize model when model/download state changes.
        val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
        LaunchedEffect(curDownloadStatus, selectedModel.name) {
            if (!navigatingUp) {
                if (curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
                    Log.d(
                        TAG,
                        "Initializing model '${selectedModel.name}' from ChatView launched effect"
                    )
                    modelManagerViewModel.initializeModel(
                        context,
                        task = task,
                        model = selectedModel
                    )
                }
            }
        }

        // Update selected model and clean up previous model when page is settled on a model page.
        LaunchedEffect(pagerState.settledPage) {
            val curSelectedModel = task.models[pagerState.settledPage]
            Log.d(
                TAG,
                "Pager settled on model '${curSelectedModel.name}' from '${selectedModel.name}'. Updating selected model.",
            )
            if (curSelectedModel.name != selectedModel.name) {
                modelManagerViewModel.cleanupModel(task = task, model = selectedModel)
            }
            modelManagerViewModel.selectModel(curSelectedModel)
        }

        LaunchedEffect(pagerState) {
            // Collect from the a snapshotFlow reading the currentPage
            snapshotFlow { pagerState.currentPage }.collect { page ->
                Log.d(
                    TAG,
                    "Page changed to $page"
                )
            }
        }

        // Handle system's edge swipe.
        BackHandler { handleNavigateUp() }

        Scaffold(
            modifier = modifier,
            topBar = {
                ModelPageAppBar(
                    task = task,
                    model = selectedModel,
                    modelManagerViewModel = modelManagerViewModel,
                    canShowResetSessionButton = true,
                    isResettingSession = uiState.isResettingSession,
                    inProgress = uiState.inProgress,
                    modelPreparing = uiState.preparing,
                    onResetSessionClicked = onResetSessionClicked,
                    onConfigChanged = { old, new ->
                        viewModel.addConfigChangedMessage(
                            oldConfigValues = old,
                            newConfigValues = new,
                            model = selectedModel,
                        )
                    },
                    onBackClicked = { handleNavigateUp() },
                    onModelSelected = { model ->
                        scope.launch { pagerState.animateScrollToPage(task.models.indexOf(model)) }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                var log by remember { mutableStateOf("") }
                var serverStatus by remember { mutableStateOf("启动服务器") }
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            val result = Server.startServer(viewModel, selectedModel, context)
                            log = log + "\n" + result.first
                            serverStatus = result.second
                        }
                    ) { Text(serverStatus) }
                    Text(log)
                }
            }
        }
    }

    private object Server {

        private var isServerStarted = false

        fun startServer(
            viewModel: LlmServerViewModel,
            selectedModel: Model,
            context: Context
        ): Pair<String, String> {
            isServerStarted = !isServerStarted
            if (isServerStarted) {
                ApiServer.startServer(viewModel.viewModelScope) { model, messages ->
                    val prompt = messages.flatMap { it.content }
                        .find { it is Content.TextContent } as Content.TextContent
                    val image = messages.flatMap { it.content }
                        .find { it is Content.ImageUrlContent } as Content.ImageUrlContent
                    return@startServer viewModel.generateResponse(
                        selectedModel,
                        prompt.text,
                        listOf()
                    )
                }
            }
            Toast.makeText(context, "启动服务器", Toast.LENGTH_SHORT).show()
            return "服务器启动成功, 正在监听 http://0.0.0.0:8080" to (if (isServerStarted) "停止服务器" else "启动服务器")
        }

    }
}