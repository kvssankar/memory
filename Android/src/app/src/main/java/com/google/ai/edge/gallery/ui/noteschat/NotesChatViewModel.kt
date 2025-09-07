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
            - type TEXT (values: 'text' or 'image' - the note type)
            - title TEXT (note title)
            - tags TEXT (comma-separated tags like 'work,personal,reminder')
            - description TEXT (user's original note content)
            - ai_description TEXT (AI-generated description of the note)
            - image_path TEXT (nullable, path to image file)
            - created_at INTEGER (epoch millis)
            - updated_at INTEGER (epoch millis)

          Important rules:
          - Output ONLY the SQL in a fenced code block like ```sql ... ``` with no extra commentary.
          - Use SELECT queries only. Do NOT modify data.
          - Always specify column names explicitly (SELECT id, title, description, ... FROM notes)
          - For content searches, look in title, description, ai_description, and tags columns
          - For tags, use LIKE patterns: tags LIKE '%reminder%'
          - For time ordering use ORDER BY updated_at DESC
          - Add LIMIT 50 for large result sets
          - The 'type' column only contains 'text' or 'image', not content keywords

          Example: For "reminders", search in title/description/tags, not type column.
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
                  You are a notes assistant answering questions about the user's personal notes.
                  
                  User question: $userText
                  SQL used: ```sql\n$sql\n```
                  Results (JSON array of rows):
                  ```json
                  $json
                  ```

                  Rules:
                  - If results exist, answer based on the notes found. Reference note titles when helpful.
                  - If no results found:
                    * For conversational queries (greetings, thanks, etc.), respond naturally
                    * For notes-related queries, say you couldn't find matching notes and suggest different phrasing
                    * For unrelated queries (general knowledge, facts), politely decline: "I can only help with questions about your notes."
                  - Be concise and helpful.
                  - Only answer about the user's notes, not general knowledge.

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
