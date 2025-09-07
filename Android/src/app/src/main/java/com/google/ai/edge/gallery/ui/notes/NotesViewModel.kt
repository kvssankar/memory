package com.google.ai.edge.gallery.ui.notes

import android.content.Context
import android.util.Log
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
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

        val initSuccess = ensureModelInitializedAsync(context, modelManagerViewModel, task, model)
        if (!initSuccess) {
          withContext(Dispatchers.Main) { onError("Failed to initialize AI model") }
          return@launch
        }

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

  fun createImageNote(
    context: Context,
    modelManagerViewModel: ModelManagerViewModel,
    bitmap: Bitmap,
    userCaption: String,
    onError: (String) -> Unit,
    onSuccess: () -> Unit,
  ) {
    viewModelScope.launch(Dispatchers.Default) {
      try {
        _isCreating.value = true
        val now = System.currentTimeMillis()
        
        // Save bitmap to local storage
        val imagePath = saveImageToLocal(context, bitmap, now)
        if (imagePath == null) {
          withContext(Dispatchers.Main) { onError("Failed to save image") }
          return@launch
        }

        val (model, task) = pickAvailableLlmModel(modelManagerViewModel)
          ?: run {
            // Fallback: simple metadata without LLM if no model available
            val fallback = Note(
              type = "image",
              title = if (userCaption.isNotEmpty()) userCaption.take(32) else "Image Note",
              tags = listOf("image"),
              description = userCaption.ifEmpty { "Image note" },
              aiDescription = userCaption.ifEmpty { "An image note" },
              imagePath = imagePath,
              createdAt = now,
              updatedAt = now,
            )
            repo.add(fallback)
            load()
            withContext(Dispatchers.Main) { onSuccess() }
            return@launch
          }

        // Ensure GPU is selected by default
        model.configValues = model.configValues.toMutableMap().apply {
          put("Choose accelerator", "GPU")
        }
        
        val initSuccess = ensureModelInitializedAsync(context, modelManagerViewModel, task, model)
        if (!initSuccess) {
          withContext(Dispatchers.Main) { onError("Failed to initialize AI model") }
          return@launch
        }

        val imagePrompt = buildImagePrompt(userCaption)
        val result = try {
          runLlmWithImage(imagePrompt, model, bitmap)
        } catch (e: Exception) {
          Log.e(TAG, "AI processing failed, using fallback", e)
          // Create fallback response if AI fails
          ""
        }
        
        val parsed = if (result.isNotEmpty()) {
          parseResultOrFallback(result, userCaption.ifEmpty { "Image note" })
        } else {
          // Fallback metadata if AI processing completely failed
          Parsed(
            title = if (userCaption.isNotEmpty()) userCaption.take(32) else "Image Note",
            tags = listOf("image"),
            aiDescription = if (userCaption.isNotEmpty()) "Image with caption: $userCaption" else "Image note",
            createdAt = now,
            updatedAt = now
          )
        }

        val note =
          Note(
            type = "image",
            title = parsed.title,
            tags = parsed.tags,
            description = userCaption.ifEmpty { "Image note" },
            aiDescription = parsed.aiDescription,
            imagePath = imagePath,
            createdAt = parsed.createdAt,
            updatedAt = parsed.updatedAt,
          )
        repo.add(note)
        load()
        withContext(Dispatchers.Main) { onSuccess() }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to create image note", e)
        withContext(Dispatchers.Main) { onError(e.message ?: "Failed to create image note") }
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

  fun deleteNote(
    noteId: Long,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val affected = repo.delete(noteId)
        _editingNote.value = null
        _notes.value = repo.all()
        withContext(Dispatchers.Main) {
          if (affected > 0) onSuccess() else onError("Note not found")
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) { onError(e.message ?: "Failed to delete note") }
      }
    }
  }

  private fun saveImageToLocal(context: Context, bitmap: Bitmap, timestamp: Long): String? {
    return try {
      val fileName = "note_image_${timestamp}.jpg"
      val file = File(context.filesDir, fileName)
      FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
      }
      file.absolutePath
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save image", e)
      null
    }
  }

  private fun buildImagePrompt(caption: String): String {
    val basePrompt = "You are an assistant creating metadata for an image note. Analyze the provided image."
    val captionText = if (caption.isNotEmpty()) "Caption: \"$caption\"" else "No caption provided."
    return "$basePrompt\n$captionText\n" +
      "Generate a compact JSON object only (no extra text) with keys:\n" +
      "- title (<=8 words describing the image content)\n" +
      "- tags (array of 1-5 lowercase tags for searching)\n" +
      "- ai_description (2-3 sentences describing what's visible in the image, including any text, objects, people, scenes, colors, and relevant details. Include caption context if provided. Write objectively without mentioning 'user' or personal references)\n" +
      "- created_at (unix ms), updated_at (unix ms)\n" +
      "Output JSON only."
  }

  private fun buildPrompt(description: String): String {
    return (
      "You are an assistant creating metadata for a short note.\n" +
        "Generate a compact JSON object only (no extra text) with keys:\n" +
        "- title (<=8 words summarizing the note content)\n" +
        "- tags (array of 1-5 lowercase tags for searching)\n" +
        "- ai_description (2-3 sentences describing the note content, including key details, context, and relevant information. Write objectively without mentioning 'user' or personal references. Focus on what the note contains)\n" +
        "- created_at (unix ms), updated_at (unix ms)\n" +
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

  private suspend fun runLlmWithImage(prompt: String, model: Model, bitmap: Bitmap): String {
    val sb = StringBuilder()
    val done = CompletableDeferred<Unit>()
    withTimeout(60_000) { // Increased timeout for image processing
      LlmChatModelHelper.runInference(
        model = model,
        input = prompt,
        images = listOf(bitmap),
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
    // For image notes, prioritize LLM_ASK_IMAGE task which supports vision
    val llmTasks = tasks.filter { 
      it.id == BuiltInTaskId.LLM_ASK_IMAGE || 
      it.id == BuiltInTaskId.LLM_CHAT || 
      it.id == BuiltInTaskId.LLM_PROMPT_LAB 
    }
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

  private suspend fun ensureModelInitializedAsync(
    context: Context,
    modelManagerViewModel: ModelManagerViewModel,
    task: Task,
    model: Model,
  ): Boolean {
    val ui = modelManagerViewModel.uiState.value
    modelManagerViewModel.selectModel(model)
    val state = ui.modelInitializationStatus[model.name]
    
    if (state != null && state.status == com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType.INITIALIZED) {
      return true // Already initialized
    }

    return try {
      // For image processing, we need to initialize with image support enabled
      val supportImage = task.id == BuiltInTaskId.LLM_ASK_IMAGE
      
      val initComplete = CompletableDeferred<Boolean>()
      
      if (supportImage) {
        // Initialize directly with image support like the ask image feature
        LlmChatModelHelper.initialize(
          context = context,
          model = model,
          supportImage = true,
          supportAudio = false,
          onDone = { error ->
            if (error.isNotEmpty()) {
              Log.e(TAG, "Failed to initialize model with image support: $error")
              initComplete.complete(false)
            } else {
              initComplete.complete(true)
            }
          }
        )
      } else {
        modelManagerViewModel.initializeModel(context, task, model)
        initComplete.complete(true)
      }
      
      // Wait for initialization with timeout
      withTimeout(15_000) { // 15 seconds timeout
        initComplete.await()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Model initialization failed", e)
      false
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
