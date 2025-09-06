package com.google.ai.edge.gallery.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.rounded.Settings
import com.google.ai.edge.gallery.data.notes.Note
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.launch
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesHomeScreen(
  modelManagerViewModel: ModelManagerViewModel,
  onOpenSettings: () -> Unit = {},
  onOpenNote: (Long) -> Unit = {},
) {
  val vm: NotesViewModel = hiltViewModel()
  val notes by vm.notes.collectAsState()
  val isCreating by vm.isCreating.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var showCreateDialog by remember { mutableStateOf(false) }
  var descriptionInput by remember { mutableStateOf("") }
  val context = LocalContext.current

  LaunchedEffect(Unit) { vm.load() }

  Scaffold(
    containerColor = Color(0xFF121212), // Soft black background
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "My Notes",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          fontSize = 28.sp,
          color = Color.White,
          modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onOpenSettings) {
          Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
        }
      }
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
      Row(modifier = Modifier.padding(end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FloatingActionButton(onClick = { showCreateDialog = true }) {
          Icon(Icons.Outlined.TextFields, contentDescription = "Add text note")
        }
        FloatingActionButton(onClick = { /* TODO: image note flow */ }) {
          Icon(Icons.Outlined.Image, contentDescription = "Add image note")
        }
      }
    },
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      if (isCreating) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
      Box(modifier = Modifier.fillMaxSize()) {
        NotesList(notes = notes, contentPadding = PaddingValues(12.dp), onOpenNote = onOpenNote)
      }
    }
  }

  if (showCreateDialog) {
    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      title = { Text("New Text Note") },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text("Enter a short description")
          Spacer(modifier = Modifier.height(8.dp))
          TextField(
            value = descriptionInput,
            onValueChange = { descriptionInput = it },
            singleLine = false,
            minLines = 4,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(12.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
              onClick = {
                val description = descriptionInput.trim()
                if (description.isEmpty()) {
                  Toast.makeText(context, "Description cannot be empty", Toast.LENGTH_SHORT).show()
                  return@Button
                }
                descriptionInput = ""
                showCreateDialog = false
                vm.createTextNote(
                  context = context,
                  modelManagerViewModel = modelManagerViewModel,
                  userDescription = description,
                  onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                  },
                  onSuccess = {
                    Toast.makeText(context, "Note created", Toast.LENGTH_SHORT).show()
                  },
                )
              }
            ) { Text("Create") }
            TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
          }
        }
      },
      confirmButton = {},
      dismissButton = {},
    )
  }
}

@Composable
private fun NotesList(
  notes: List<Note>,
  contentPadding: PaddingValues,
  onOpenNote: (Long) -> Unit,
) {
  if (notes.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("No notes yet. Tap + to add one.")
    }
  } else {
    val cardPalette = listOf(
      Color(0xFFF9A691),
      Color(0xFFF9F99C),
      Color(0xFFCDEEF0),
      Color(0xFFF6E8B6),
      Color(0xFFEAF7F4),
      Color(0xFFF5FDFA),
    )
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      itemsIndexed(notes) { index, note ->
        val bg = cardPalette[index % cardPalette.size]
        NoteCard(note = note, backgroundColor = bg, onClick = { onOpenNote(note.id) })
      }
    }
  }
}

@Composable
private fun NoteCard(note: Note, backgroundColor: Color, onClick: () -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = backgroundColor), modifier = Modifier.clickable { onClick() }) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
      Text(
        text = note.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = Color.Black,
      )
      Spacer(modifier = Modifier.height(4.dp))
      if (note.tags.isNotEmpty()) {
        TagRow(tags = note.tags, labelColor = Color.Black)
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = note.aiDescription,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        color = Color.Black,
      )
    }
  }
}

@Composable
private fun TagRow(tags: List<String>, labelColor: Color) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    tags.take(6).forEach { tag ->
      val label = tag.trim()
      if (label.isNotEmpty()) {
        AssistChip(
          onClick = {},
          enabled = false,
          label = { Text(label, color = labelColor) },
          colors = AssistChipDefaults.assistChipColors(
            containerColor = Color.Transparent,
            labelColor = labelColor,
            disabledLabelColor = labelColor,
          ),
        )
      }
    }
  }
}
