package com.google.ai.edge.gallery.ui.noteschat

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.Composable
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

