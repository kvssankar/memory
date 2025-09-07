package com.google.ai.edge.gallery.ui.noteschat

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.bundleOf
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.firebaseAnalytics
import com.google.ai.edge.gallery.ui.common.chat.ChatMessage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatView
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

@Composable
fun NotesChatScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: NotesChatViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val task = modelManagerViewModel.getTaskById(id = BuiltInTaskId.LLM_CHAT)!!
  val uiState by modelManagerViewModel.uiState.collectAsState()

  // Auto-select gemma-3n-e2b-it model by default for notes chat
  LaunchedEffect(task.models) {
    val preferredModelName = "gemma-3n-e2b-it"
    val selectedModel = uiState.selectedModel
    
    // Try to find gemma-3n-e2b-it first, then any gemma model, then first available
    val targetModel = task.models.find { it.name.contains(preferredModelName, ignoreCase = true) }
      ?: task.models.find { it.name.contains("gemma", ignoreCase = true) }
      ?: task.models.firstOrNull()
    
    if (targetModel != null && selectedModel.name != targetModel.name) {
      modelManagerViewModel.selectModel(targetModel)
      // Set GPU as default with CPU fallback by ensuring accelerator config is set to GPU
      targetModel.configValues = targetModel.configValues.toMutableMap().apply {
        put("Choose accelerator", "GPU")
      }
    }
  }

  ChatView(
    task = task,
    viewModel = viewModel,
    modelManagerViewModel = modelManagerViewModel,
    onSendMessage = { model, messages ->
      // We only consider the last text message as the user query
      val text = messages.lastOrNull { it is ChatMessageText }?.let { (it as ChatMessageText).content } ?: ""
      messages.forEach { msg -> viewModel.addMessage(model = model, message = msg) }
      if (text.isNotBlank()) {
        modelManagerViewModel.addTextInputHistory(text)
        viewModel.chatWithNotes(model = model, userText = text)
        firebaseAnalytics?.logEvent(
          "generate_action",
          bundleOf("capability_name" to "notes_chat", "model_id" to model.name),
        )
      }
    },
    onRunAgainClicked = { model, message ->
      if (message is ChatMessageText) {
        viewModel.chatWithNotes(model = model, userText = message.content)
      }
    },
    onBenchmarkClicked = { _, _, _, _ -> },
    onResetSessionClicked = { /* not exposed here */ },
    showStopButtonInInputWhenInProgress = false,
    onStopButtonClicked = { /* not used */ },
    navigateUp = navigateUp,
    modifier = modifier,
  )
}

