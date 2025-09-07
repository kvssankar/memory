package com.google.ai.edge.gallery.ui.noteschat

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.notes.NotesRepository
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageLoading
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.common.chat.ChatViewModel
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "AGNotesChatVM"

@HiltViewModel
class NotesChatViewModel @Inject constructor(
  private val notesRepository: NotesRepository
) : ChatViewModel() {

  fun chatWithNotes(model: Model, userText: String) {
    viewModelScope.launch(Dispatchers.Default) {
      setInProgress(true)
      // Echo user text into the conversation
      val accelerator = model.getStringConfigValue(key = ConfigKeys.ACCELERATOR, defaultValue = "")
      addMessage(model = model, message = ChatMessageText(content = userText, side = ChatSide.USER))
      addMessage(model = model, message = ChatMessageLoading(accelerator = accelerator))

      // Wait until model instance exists
      while (model.instance == null) { delay(100) }
      delay(200)

      try {
        val schemaDesc = """
          You are a data assistant. Generate ONLY a single SQLite SELECT query for the following schema.
          Table: notes
          Columns:
            - id INTEGER PRIMARY KEY
            - type TEXT
            - title TEXT
            - tags TEXT (comma-separated tags)
            - description TEXT
            - ai_description TEXT
            - image_path TEXT (nullable)
            - created_at INTEGER (epoch millis)
            - updated_at INTEGER (epoch millis)

          Constraints:
          - Output ONLY the SQL in a fenced code block like ```sql ... ``` with no extra commentary.
          - Use SELECT queries only. Do NOT modify data.
          - Prefer LIMIT 50 when returning lists.
          - For time ordering prefer ORDER BY updated_at DESC.
          - For tags, they are comma-separated; use LIKE patterns to match tags, e.g., tags LIKE '%tag%'.
        """.trimIndent()

        val sqlPrompt = "$schemaDesc\n\nUser question: $userText\n\nGenerate the SQL now."

        val firstResponse = StringBuilder()
        LlmChatModelHelper.runInference(
          model = model,
          input = sqlPrompt,
          resultListener = { partial, done ->
            firstResponse.append(partial)
            if (done) {
              // Proceed to execute when done
              viewModelScope.launch(Dispatchers.Default) {
                val sql = extractSql(firstResponse.toString()).trim()
                if (sql.isEmpty() || !sql.lowercase().startsWith("select")) {
                  setInProgress(false)
                  replaceLastMessage(
                    model = model,
                    message = ChatMessageText(
                      content = "Sorry, I couldn’t generate a valid SQL for that question.",
                      side = ChatSide.AGENT
                    ),
                    type = com.google.ai.edge.gallery.ui.common.chat.ChatMessageType.LOADING
                  )
                  return@launch
                }

                val rows = try {
                  notesRepository.runSelect(sql)
                } catch (e: Exception) {
                  Log.e(TAG, "SQL execution failed", e)
                  setInProgress(false)
                  replaceLastMessage(
                    model = model,
                    message = ChatMessageText(
                      content = "SQL error: ${e.message}",
                      side = ChatSide.AGENT
                    ),
                    type = com.google.ai.edge.gallery.ui.common.chat.ChatMessageType.LOADING
                  )
                  return@launch
                }

                val json = rowsToJson(rows)
                val answerPrompt = """
                  You are answering a user using the results from an SQLite query on their notes.
                  - Be concise and helpful.
                  - If appropriate, reference note titles.
                  - If nothing matches, explain briefly and suggest different phrasing.

                  User question: $userText
                  SQL used: ```sql\n$sql\n```
                  Results (JSON array of rows):
                  ```json
                  $json
                  ```

                  Provide the final answer for the user.
                """.trimIndent()

                // Stream final answer
                var started = false
                LlmChatModelHelper.runInference(
                  model = model,
                  input = answerPrompt,
                  resultListener = { partial, done ->
                    if (!started) {
                      // Replace loading with an empty agent message to stream into
                      replaceLastMessage(
                        model = model,
                        message = ChatMessageText(content = "", side = ChatSide.AGENT, accelerator = accelerator),
                        type = com.google.ai.edge.gallery.ui.common.chat.ChatMessageType.LOADING
                      )
                      started = true
                    }
                    updateLastTextMessageContentIncrementally(
                      model = model,
                      partialContent = partial,
                      latencyMs = if (done) 0f else -1f,
                    )
                    if (done) {
                      setInProgress(false)
                    }
                  },
                  cleanUpListener = { setInProgress(false) }
                )
              }
            }
          },
          cleanUpListener = { setInProgress(false) }
        )
      } catch (e: Exception) {
        Log.e(TAG, "Error in chatWithNotes", e)
        setInProgress(false)
        replaceLastMessage(
          model = model,
          message = ChatMessageText(content = "Unexpected error: ${e.message}", side = ChatSide.AGENT),
          type = com.google.ai.edge.gallery.ui.common.chat.ChatMessageType.LOADING
        )
      }
    }
  }

  private fun extractSql(text: String): String {
    // Try to find fenced block ```sql ... ``` first
    val fenceRegex = Regex("```sql\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
    val m1 = fenceRegex.find(text)
    if (m1 != null) return m1.groupValues[1]
    // Fallback: any fenced block
    val anyFence = Regex("```\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(text)
    if (anyFence != null) return anyFence.groupValues[1]
    // Fallback: heuristic - first SELECT...; statement
    val sel = Regex("(?is)(select[\\s\\S]*?)(;|$)").find(text)?.groupValues?.getOrNull(1) ?: ""
    return sel
  }

  private fun rowsToJson(rows: List<Map<String, Any?>>): String {
    val arr = JSONArray()
    for (row in rows) {
      val obj = JSONObject()
      for ((k, v) in row) {
        obj.put(k, v)
      }
      arr.put(obj)
    }
    return arr.toString()
  }
}
