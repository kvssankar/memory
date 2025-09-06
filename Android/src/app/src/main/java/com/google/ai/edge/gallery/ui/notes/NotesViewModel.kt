package com.google.ai.edge.gallery.ui.notes

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.notes.Note
import com.google.ai.edge.gallery.data.notes.NotesRepository
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val TAG = "AGNotesVM"

@HiltViewModel
class NotesViewModel @Inject constructor(private val repo: NotesRepository) : ViewModel() {
  private val _notes = MutableStateFlow<List<Note>>(emptyList())
  val notes = _notes.asStateFlow()
  private val _isCreating = MutableStateFlow(false)
  val isCreating = _isCreating.asStateFlow()
  private val _editingNote = MutableStateFlow<Note?>(null)
  val editingNote = _editingNote.asStateFlow()

  fun load() {
    viewModelScope.launch(Dispatchers.IO) { _notes.value = repo.all() }
  }

  fun createTextNote(
    context: Context,
    modelManagerViewModel: ModelManagerViewModel,
    userDescription: String,
    onError: (String) -> Unit,
    onSuccess: () -> Unit,
  ) {
    viewModelScope.launch(Dispatchers.Default) {
      try {
        _isCreating.value = true
        val now = System.currentTimeMillis()
        val (model, task) = pickAvailableLlmModel(modelManagerViewModel)
          ?: run {
            // Fallback: simple metadata without LLM if no model available
            val fallback = Note(
              type = "text",
              title = userDescription.take(32),
              tags = listOf("note"),
              description = userDescription,
              aiDescription = userDescription,
              createdAt = now,
              updatedAt = now,
            )
            repo.add(fallback)
            load()
            withContext(Dispatchers.Main) { onSuccess() }
            return@launch
          }

        ensureModelInitialized(context, modelManagerViewModel, task, model)

        val prompt = buildPrompt(userDescription)
        val result = runLlm(prompt, model)
        val parsed = parseResultOrFallback(result, userDescription)

        val note =
          Note(
            type = "text",
            title = parsed.title,
            tags = parsed.tags,
            description = userDescription,
            aiDescription = parsed.aiDescription,
            createdAt = parsed.createdAt,
            updatedAt = parsed.updatedAt,
          )
        repo.add(note)
        load()
        withContext(Dispatchers.Main) { onSuccess() }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to create note", e)
        withContext(Dispatchers.Main) { onError(e.message ?: "Failed to create note") }
      } finally {
        _isCreating.value = false
      }
    }
  }

  fun loadNote(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { _editingNote.value = repo.getById(id) }
  }

  fun updateNote(
    noteId: Long,
    newTitle: String,
    newDescription: String,
    newTags: List<String>,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val existing = repo.getById(noteId) ?: run {
          withContext(Dispatchers.Main) { onError("Note not found") }
          return@launch
        }
        val now = System.currentTimeMillis()
        val updated =
          existing.copy(
            title = newTitle,
            description = newDescription,
            tags = newTags.map { it.trim() }.filter { it.isNotEmpty() },
            updatedAt = now,
          )
        repo.update(updated)
        _editingNote.value = updated
        // Refresh list view state too
        _notes.value = repo.all()
        withContext(Dispatchers.Main) { onSuccess() }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) { onError(e.message ?: "Failed to update note") }
      }
    }
  }

  private fun buildPrompt(description: String): String {
    return (
      "You are an assistant creating metadata for a short note.\n" +
        "Given the user's short description, generate a compact JSON object only (no extra text) " +
        "with keys: title (<=8 words), tags (array of 1-5 lowercase tags), ai_description (1-2 sentences), " +
        "created_at (unix ms), updated_at (unix ms).\n" +
        "Description: \"" + description.replace("\"", "'") + "\"\n" +
        "Output JSON only."
    )
  }

  private data class Parsed(
    val title: String,
    val tags: List<String>,
    val aiDescription: String,
    val createdAt: Long,
    val updatedAt: Long,
  )

  private fun parseResultOrFallback(result: String, desc: String): Parsed {
    val jsonText = sanitizeToJson(result)
    return try {
      val obj = JSONObject(jsonText)
      val title = obj.optString("title").ifBlank { desc.take(32) }
      val aiDesc = obj.optString("ai_description", desc)
      val tagsArray = obj.optJSONArray("tags")
      val tags = mutableListOf<String>()
      if (tagsArray != null) {
        for (i in 0 until tagsArray.length()) {
          tags.add(tagsArray.optString(i).trim().lowercase())
        }
      }
      val now = System.currentTimeMillis()
      val created = obj.optLong("created_at", now)
      val updated = obj.optLong("updated_at", created)
      Parsed(title = title, tags = if (tags.isEmpty()) listOf("note") else tags, aiDescription = aiDesc, createdAt = created, updatedAt = updated)
    } catch (e: Exception) {
      Parsed(
        title = desc.take(32),
        tags = listOf("note"),
        aiDescription = desc,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
      )
    }
  }

  private suspend fun runLlm(prompt: String, model: Model): String {
    val sb = StringBuilder()
    val done = CompletableDeferred<Unit>()
    withTimeout(30_000) {
      LlmChatModelHelper.runInference(
        model = model,
        input = prompt,
        resultListener = { partial, isDone ->
          sb.append(partial)
          if (isDone && !done.isCompleted) done.complete(Unit)
        },
        cleanUpListener = {},
      )
      done.await()
    }
    return sb.toString()
  }

  private fun pickAvailableLlmModel(
    modelManagerViewModel: ModelManagerViewModel
  ): Pair<Model, Task>? {
    val ui = modelManagerViewModel.uiState.value
    val tasks = ui.tasks
    val llmTasks = tasks.filter { it.id == BuiltInTaskId.LLM_CHAT || it.id == BuiltInTaskId.LLM_PROMPT_LAB }
    for (task in llmTasks) {
      for (model in task.models) {
        val status = ui.modelDownloadStatus[model.name]?.status
        if (status == ModelDownloadStatusType.SUCCEEDED) {
          return model to task
        }
      }
    }
    return null
  }

  private fun ensureModelInitialized(
    context: Context,
    modelManagerViewModel: ModelManagerViewModel,
    task: Task,
    model: Model,
  ) {
    val ui = modelManagerViewModel.uiState.value
    modelManagerViewModel.selectModel(model)
    val state = ui.modelInitializationStatus[model.name]
    if (state == null || state.status != com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType.INITIALIZED) {
      modelManagerViewModel.initializeModel(context, task, model)
      // Busy-wait until the instance exists; then small settle delay like chat flow
      var tries = 0
      while (tries < 1000 && model.instance == null) { // up to ~10s
        Thread.sleep(10)
        tries++
      }
      if (model.instance != null) {
        try { Thread.sleep(500) } catch (_: InterruptedException) {}
      }
    }
  }

  private fun sanitizeToJson(text: String): String {
    val normalized = com.google.ai.edge.gallery.common.processLlmResponse(text).trim()
    val codeFenceRegex = Regex("```[a-zA-Z]*")
    val noFences = normalized.replace(codeFenceRegex, "").replace("```", "").trim()
    val start = noFences.indexOf('{')
    val end = noFences.lastIndexOf('}')
    return if (start >= 0 && end > start) noFences.substring(start, end + 1) else noFences
  }
}
