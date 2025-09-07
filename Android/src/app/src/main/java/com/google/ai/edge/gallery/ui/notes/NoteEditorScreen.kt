package com.google.ai.edge.gallery.ui.notes

// removed shimmer animation imports
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
// removed TextFieldDefaults to avoid compatibility issues with older material3
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
// removed Brush/Offset/onGloballyPositioned imports after shimmer removal
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
  noteId: Long,
  onNavigateUp: () -> Unit,
) {
  val vm: NotesViewModel = hiltViewModel()
  val editingNote by vm.editingNote.collectAsState()

  LaunchedEffect(noteId) { vm.loadNote(noteId) }

  val pastelBg = Color(0xFFCDEEF0)

  var isEditingTitle by rememberSaveable { mutableStateOf(false) }
  var isEditingDesc by rememberSaveable { mutableStateOf(false) }

  var title by rememberSaveable(editingNote?.id) { mutableStateOf(editingNote?.title ?: "") }
  var desc by rememberSaveable(editingNote?.id) { mutableStateOf(editingNote?.description ?: "") }
  var tags by rememberSaveable(editingNote?.id) { mutableStateOf(editingNote?.tags ?: emptyList()) }
  var showAddTagDialog by remember { mutableStateOf(false) }
  var newTagText by remember { mutableStateOf("") }
  var showDeleteDialog by remember { mutableStateOf(false) }

  // Keep local state in sync when note loads
  LaunchedEffect(editingNote?.id) {
    editingNote?.let {
      title = it.title
      desc = it.description
      tags = it.tags
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(pastelBg)
      .statusBarsPadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
      // Title on main screen (black)
      if (isEditingTitle) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          singleLine = false,
          label = { Text("Title") },
          textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Color.Black),
          modifier = Modifier.fillMaxWidth()
        )
      } else {
        Text(
          text = if (title.isBlank()) "Untitled Note" else title,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = Color.Black,
          modifier = Modifier.fillMaxWidth().clickable { isEditingTitle = true }
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Description on main screen (black)
      if (isEditingDesc) {
        OutlinedTextField(
          value = desc,
          onValueChange = { desc = it },
          singleLine = false,
          minLines = 4,
          label = { Text("Description") },
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
          modifier = Modifier.fillMaxWidth()
        )
      } else {
        Text(
          text = if (desc.isBlank()) "Tap to add a description" else desc,
          style = MaterialTheme.typography.bodyLarge,
          color = Color.Black,
          modifier = Modifier.fillMaxWidth().clickable { isEditingDesc = true }
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Show image if this is an image note
      editingNote?.let { note ->
        if (note.type == "image" && !note.imagePath.isNullOrEmpty()) {
          NoteEditorImage(imagePath = note.imagePath)
          Spacer(modifier = Modifier.height(12.dp))
        }
      }

      // (AI Description moved below the Tags section)

      // Tags box with white background
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White, shape = RoundedCornerShape(10.dp))
          .padding(horizontal = 16.dp, vertical = 16.dp)
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          // Tags header row with Add button at opposite ends
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Tags",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Color.Black
            )
            TextButton(onClick = { showAddTagDialog = true }) {
              Icon(Icons.Default.Add, contentDescription = "Add tag", tint = Color.Black)
              Text("Add tag", color = Color.Black)
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Tags as larger chips
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            tags.forEach { tag ->
              Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                  Text(
                    tag,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.Black,
                    style = MaterialTheme.typography.labelLarge
                  )
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove tag",
                    tint = Color.Black,
                    modifier = Modifier
                      .padding(start = 6.dp)
                      .size(16.dp)
                      .clickable { tags = tags.filter { it != tag } }
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // AI Description (read-only) with shimmer under tags
      val aiDesc = editingNote?.aiDescription ?: ""
      AiDescriptionPanel(
        text = if (aiDesc.isBlank()) "No AI description" else aiDesc
      )
    }


    // Delete button on bottom-left
    Surface(
      color = Color.White,
      shape = RoundedCornerShape(50),
      tonalElevation = 4.dp,
      shadowElevation = 8.dp,
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(start = 16.dp, bottom = 24.dp)
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        Button(
          onClick = { showDeleteDialog = true },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
          ),
          shape = RoundedCornerShape(50),
        ) { Text("Delete") }
      }
    }
    // Floating action bar
    Surface(
      color = Color.White,
      shape = RoundedCornerShape(50),
      tonalElevation = 4.dp,
      shadowElevation = 8.dp,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(bottom = 24.dp)
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        Button(
          onClick = { onNavigateUp() },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = Color.Black
          ),
          shape = RoundedCornerShape(50),
        ) { Text("Cancel") }
        Button(
          onClick = {
            val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }
            vm.updateNote(
              noteId = noteId,
              newTitle = title.trim().ifBlank { "Untitled" },
              newDescription = desc.trim(),
              newTags = cleanTags,
              onSuccess = { onNavigateUp() },
              onError = { /* TODO: snackbar */ },
            )
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          shape = RoundedCornerShape(50),
        ) { Text("Update") }
      }
    }

    if (showAddTagDialog) {
      AlertDialog(
        onDismissRequest = { showAddTagDialog = false },
        title = { Text("Add Tag") },
        text = {
          OutlinedTextField(
            value = newTagText,
            onValueChange = { newTagText = it },
            singleLine = true,
            label = { Text("Tag") },
          )
        },
        confirmButton = {
          TextButton(onClick = {
            val t = newTagText.trim()
            if (t.isNotEmpty()) tags = (tags + t).distinct()
            newTagText = ""
            showAddTagDialog = false
          }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = { showAddTagDialog = false }) { Text("Cancel") } },
      )
    }

    if (showDeleteDialog) {
      AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("Delete note?") },
        text = { Text("This action cannot be undone.") },
        confirmButton = {
          Button(
            onClick = {
              showDeleteDialog = false
              vm.deleteNote(
                noteId = noteId,
                onSuccess = { onNavigateUp() },
                onError = { /* TODO: snackbar */ },
              )
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError
            )
          ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
      )
    }
}
}

@Composable
private fun AiDescriptionPanel(text: String) {
  val bg = Color(0xFFE8E0FF) // light violet
  // Shimmer removed

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(bg, shape = RoundedCornerShape(10.dp))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {}
      )
      .padding(horizontal = 16.dp, vertical = 16.dp)
  ) {
    // Content
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = "AI Description",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = Color.Black,
        fontSize = 16.sp,
      )
    }

    // Shimmer removed
  }
}

@Composable
private fun NoteEditorImage(imagePath: String) {
  val bitmap = remember(imagePath) {
    try {
      BitmapFactory.decodeFile(imagePath)
    } catch (e: Exception) {
      null
    }
  }
  
  bitmap?.let {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color.White, shape = RoundedCornerShape(10.dp))
        .padding(16.dp)
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "Image",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Color.Black,
          modifier = Modifier.padding(bottom = 12.dp)
        )
        Image(
          bitmap = it.asImageBitmap(),
          contentDescription = "Note image",
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
          contentScale = ContentScale.Fit
        )
      }
    }
  }
}





