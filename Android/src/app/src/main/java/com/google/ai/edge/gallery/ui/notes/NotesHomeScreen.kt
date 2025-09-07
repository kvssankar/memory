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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TextFields
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.ui.common.createTempPictureUri
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
  onOpenChat: () -> Unit = {},
  refreshKey: Int = 0,
) {
  val vm: NotesViewModel = hiltViewModel()
  val notes by vm.notes.collectAsState()
  val isCreating by vm.isCreating.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var showCreateDialog by remember { mutableStateOf(false) }
  var showImageCaptureDialog by remember { mutableStateOf(false) }
  var descriptionInput by remember { mutableStateOf("") }
  var captionInput by remember { mutableStateOf("") }
  var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var tempPhotoUri by remember { mutableStateOf(value = Uri.EMPTY) }
  val context = LocalContext.current

  // Image capture launchers
  val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { isImageSaved ->
    if (isImageSaved) {
      handleImageCaptured(context, tempPhotoUri) { bitmap ->
        capturedBitmap = bitmap
        showImageCaptureDialog = true
      }
    }
  }

  val takePicturePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted ->
    if (permissionGranted) {
      tempPhotoUri = context.createTempPictureUri()
      cameraLauncher.launch(tempPhotoUri)
    }
  }

  val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) {
      handleImageCaptured(context, uri) { bitmap ->
        capturedBitmap = bitmap
        showImageCaptureDialog = true
      }
    }
  }

  LaunchedEffect(refreshKey) { vm.load() }

  Scaffold(
    containerColor = Color.White, // Light background
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
          color = Color.Black,
          modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onOpenChat) {
          Icon(imageVector = Icons.Rounded.Chat, contentDescription = "Chat with Notes", tint = Color.Black)
        }
        IconButton(onClick = onOpenSettings) {
          Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.Black)
        }
      }
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
      Row(modifier = Modifier.padding(end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FloatingActionButton(onClick = { showCreateDialog = true }) {
          Icon(Icons.Outlined.TextFields, contentDescription = "Add text note")
        }
        FloatingActionButton(onClick = { 
          // Check camera permission and launch camera
          when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
              tempPhotoUri = context.createTempPictureUri()
              cameraLauncher.launch(tempPhotoUri)
            }
            else -> {
              takePicturePermissionLauncher.launch(Manifest.permission.CAMERA)
            }
          }
        }) {
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
        NotesList(
          notes = notes,
          contentPadding = PaddingValues(12.dp),
          onOpenNote = onOpenNote,
          onOpenSettings = onOpenSettings,
        )
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

  if (showImageCaptureDialog && capturedBitmap != null) {
    ImageCaptureDialog(
      bitmap = capturedBitmap!!,
      caption = captionInput,
      onCaptionChange = { captionInput = it },
      onConfirm = {
        showImageCaptureDialog = false
        vm.createImageNote(
          context = context,
          modelManagerViewModel = modelManagerViewModel,
          bitmap = capturedBitmap!!,
          userCaption = captionInput.trim(),
          onError = { err ->
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
          },
          onSuccess = {
            Toast.makeText(context, "Image note created", Toast.LENGTH_SHORT).show()
          },
        )
        capturedBitmap = null
        captionInput = ""
      },
      onCancel = {
        showImageCaptureDialog = false
        capturedBitmap = null
        captionInput = ""
      }
    )
  }
}

@Composable
private fun NotesList(
  notes: List<Note>,
  contentPadding: PaddingValues,
  onOpenNote: (Long) -> Unit,
  onOpenSettings: () -> Unit,
) {
  if (notes.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(24.dp)
      ) {
        Text(
          "There are no notes",
          color = Color.Black,
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          "You need to get a Hugging Face token and download the model once.",
          color = Color.Black,
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenSettings) { Text("Get Started") }
      }
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
      
      // Show image if this is an image note
      if (note.type == "image" && !note.imagePath.isNullOrEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        NoteImage(imagePath = note.imagePath, modifier = Modifier.fillMaxWidth())
      }
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

@Composable
private fun NoteImage(
  imagePath: String,
  modifier: Modifier = Modifier,
  maxHeight: androidx.compose.ui.unit.Dp = 150.dp
) {
  val bitmap = remember(imagePath) {
    try {
      BitmapFactory.decodeFile(imagePath)
    } catch (e: Exception) {
      null
    }
  }
  
  bitmap?.let {
    Image(
      bitmap = it.asImageBitmap(),
      contentDescription = "Note image",
      modifier = modifier
        .heightIn(max = maxHeight)
        .clip(RoundedCornerShape(8.dp)),
      contentScale = ContentScale.Crop
    )
  }
}

@Composable
private fun ImageCaptureDialog(
  bitmap: Bitmap,
  caption: String,
  onCaptionChange: (String) -> Unit,
  onConfirm: () -> Unit,
  onCancel: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onCancel,
    title = { Text("New Image Note") },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        // Show captured image
        androidx.compose.foundation.Image(
          bitmap = bitmap.asImageBitmap(),
          contentDescription = "Captured image",
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
          contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Add an optional caption")
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
          value = caption,
          onValueChange = onCaptionChange,
          placeholder = { Text("Optional caption...") },
          singleLine = false,
          maxLines = 3,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onCancel) { Text("Cancel") }
        Button(onClick = onConfirm) { Text("Create Note") }
      }
    },
    dismissButton = {}
  )
}

private fun handleImageCaptured(
  context: Context,
  uri: Uri,
  onSuccess: (Bitmap) -> Unit
) {
  try {
    val inputStream = context.contentResolver.openInputStream(uri)
    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
    if (bitmap != null) {
      // Rotate if needed for portrait orientation
      val finalBitmap = if (bitmap.width > bitmap.height) {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(90f)
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
      } else {
        bitmap
      }
      onSuccess(finalBitmap)
    }
  } catch (e: Exception) {
    e.printStackTrace()
  }
}
