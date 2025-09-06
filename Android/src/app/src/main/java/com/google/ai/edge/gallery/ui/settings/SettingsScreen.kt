/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.settings

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.BuildConfig
import com.google.ai.edge.gallery.GalleryTopAppBar
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.proto.Theme
import com.google.ai.edge.gallery.ui.common.DownloadAndTryButton
import com.google.ai.edge.gallery.ui.common.tos.TosDialog
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.ThemeSettings
import com.google.ai.edge.gallery.ui.theme.labelSmallNarrow
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

private val THEME_OPTIONS = listOf(Theme.THEME_AUTO, Theme.THEME_LIGHT, Theme.THEME_DARK)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  modelManagerViewModel: ModelManagerViewModel,
  onNavigateUp: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTheme by remember { mutableStateOf(modelManagerViewModel.readThemeOverride()) }
  var hfToken by remember { mutableStateOf(modelManagerViewModel.getTokenStatusAndData().data) }
  val dateFormatter = remember {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
      .withZone(ZoneId.systemDefault())
      .withLocale(Locale.getDefault())
  }
  var customHfToken by remember { mutableStateOf("") }
  var isFocused by remember { mutableStateOf(false) }
  val focusRequester = remember { FocusRequester() }
  val interactionSource = remember { MutableInteractionSource() }
  var showTos by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      GalleryTopAppBar(
        title = "Settings",
        leftAction = com.google.ai.edge.gallery.data.AppBarAction(
          actionType = com.google.ai.edge.gallery.data.AppBarActionType.NAVIGATE_UP,
          actionFn = onNavigateUp,
        ),
      )
    },
    modifier = modifier,
  ) { innerPadding ->
    val context = LocalContext.current
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(horizontal = 20.dp, vertical = 16.dp)
          .verticalScroll(rememberScrollState())
          .clickable(interactionSource = interactionSource, indication = null) {},
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Version subtitle.
      Text(
        "App version: ${BuildConfig.VERSION_NAME}",
        style = labelSmallNarrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // Theme switcher.
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          "Theme",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
        )
        MultiChoiceSegmentedButtonRow {
          THEME_OPTIONS.forEachIndexed { index, theme ->
            SegmentedButton(
              shape = SegmentedButtonDefaults.itemShape(index = index, count = THEME_OPTIONS.size),
              onCheckedChange = {
                selectedTheme = theme
                ThemeSettings.themeOverride.value = theme
                modelManagerViewModel.saveThemeOverride(theme)

                val uiModeManager =
                  context.applicationContext.getSystemService(Context.UI_MODE_SERVICE)
                    as UiModeManager
                if (theme == Theme.THEME_AUTO) {
                  uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
                } else if (theme == Theme.THEME_LIGHT) {
                  uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
                } else {
                  uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
                }
              },
              checked = theme == selectedTheme,
              label = { Text(themeLabel(theme)) },
            )
          }
        }
      }

      // HF Token management.
      Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          "HuggingFace access token",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
        )
        val curHfToken = hfToken
        if (curHfToken != null && curHfToken.accessToken.isNotEmpty()) {
          Text(
            curHfToken.accessToken.substring(0, min(16, curHfToken.accessToken.length)) + "...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            "Expires at: ${dateFormatter.format(Instant.ofEpochMilli(curHfToken.expiresAtMs))}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Text(
            "Not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            "The token will be automatically retrieved when a gated model is downloaded",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          OutlinedButton(
            onClick = {
              modelManagerViewModel.clearAccessToken()
              hfToken = null
            },
            enabled = curHfToken != null,
          ) { Text("Clear") }

          BasicTextField(
            value = customHfToken,
            singleLine = true,
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused },
            onValueChange = { customHfToken = it },
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
          ) { innerTextField ->
            Box(
              modifier =
                Modifier
                  .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color =
                      if (isFocused) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                  )
                  .height(40.dp),
              contentAlignment = Alignment.CenterStart,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                  if (customHfToken.isEmpty()) {
                    Text(
                      "Enter token manually",
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      style = MaterialTheme.typography.bodySmall,
                    )
                  }
                  innerTextField()
                }
                if (customHfToken.isNotEmpty()) {
                  IconButton(
                    onClick = {
                      modelManagerViewModel.saveAccessToken(
                        accessToken = customHfToken,
                        refreshToken = "",
                        expiresAt = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 10,
                      )
                      hfToken = modelManagerViewModel.getTokenStatusAndData().data
                    },
                  ) { Icon(Icons.Rounded.CheckCircle, contentDescription = "") }
                }
              }
            }
          }
        }
      }

      // Quick model downloads section.
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "Quick model downloads",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
        )

        // Ask Image: gemma-3n-e2b-it
        QuickDownloadCard(
          title = "Ask Image: gemma-3n-e2b-it",
          taskId = BuiltInTaskId.LLM_ASK_IMAGE,
          modelName = "gemma-3n-e2b-it",
          modelManagerViewModel = modelManagerViewModel,
        )

        // Chat: gemma3-1b-it
        QuickDownloadCard(
          title = "Chat: gemma3-1b-it",
          taskId = BuiltInTaskId.LLM_CHAT,
          modelName = "gemma3-1b-it",
          modelManagerViewModel = modelManagerViewModel,
        )
      }

      // Third party licenses
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          "Third-party libraries",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
        )
        OutlinedButton(onClick = {
          val intent = Intent(context, OssLicensesMenuActivity::class.java)
          context.startActivity(intent)
        }) { Text("View licenses") }
      }

      // TOS
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          stringResource(R.string.settings_dialog_tos_title),
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
        )
        OutlinedButton(onClick = { showTos = true }) { Text("View Terms of Services") }
      }

      // Close button
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(onClick = onNavigateUp) { Text("Done") }
      }
    }
  }

  if (showTos) {
    TosDialog(onTosAccepted = { showTos = false }, viewingMode = true)
  }
}

@Composable
private fun QuickDownloadCard(
  title: String,
  taskId: String,
  modelName: String,
  modelManagerViewModel: ModelManagerViewModel,
) {
  val uiState by modelManagerViewModel.uiState.collectAsState()
  val task = modelManagerViewModel.getTaskById(taskId)

  // Try to resolve the model name once allowlist is loaded.
  val resolvedModel = remember(uiState.tasks, modelName) {
    // Exact match first
    modelManagerViewModel.getModelByName(modelName)
      ?: run {
        // Try relaxed matching within the target task's models
        val modelsInTask = task?.models ?: emptyList()
        val normalize: (String) -> String = { it.lowercase().replace("-", "").replace("_", "") }
        val target = normalize(modelName)
        // Try alias candidates
        val aliases: List<String> = when (modelName.lowercase()) {
          "gemma3-1b-it" -> listOf("gemma-3-1b-it", "gemma3-1bit")
          "gemma-3n-e2b-it" -> listOf("gemma-3-2b-it", "gemma-3n-2b-it", "gemma-3-vision-2b-it")
          else -> emptyList()
        }
        modelsInTask.firstOrNull { normalize(it.name) == target }
          ?: aliases.map(normalize).mapNotNull { alias -> modelsInTask.firstOrNull { normalize(it.name) == alias } }.firstOrNull()
          ?: modelsInTask.firstOrNull { normalize(it.name).contains(target) }
      }
  }

  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      when {
        uiState.loadingModelAllowlist -> {
          Text(
            "Loading model list...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        task == null -> {
          Text(
            "Task not available in this build.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        resolvedModel != null -> {
          val status = uiState.modelDownloadStatus[resolvedModel.name]
          DownloadAndTryButton(
            task = task,
            model = resolvedModel,
            enabled = true,
            downloadStatus = status,
            modelManagerViewModel = modelManagerViewModel,
            onClicked = {},
            canShowTryIt = false,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        else -> {
          Text(
            "Model not found in allowlist for this version.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

private fun themeLabel(theme: Theme): String {
  return when (theme) {
    Theme.THEME_AUTO -> "Auto"
    Theme.THEME_LIGHT -> "Light"
    Theme.THEME_DARK -> "Dark"
    else -> "Unknown"
  }
}
