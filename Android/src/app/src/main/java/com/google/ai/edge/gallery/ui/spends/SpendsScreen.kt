package com.google.ai.edge.gallery.ui.spends

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendsScreen(
    onOpenSettings: () -> Unit = {},
    onOpenSpendChat: () -> Unit = {},
) {
    Scaffold(
        containerColor = Color.White,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Spending Analytics",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSpendChat) {
                    Icon(imageVector = Icons.Rounded.Chat, contentDescription = "Chat with Spends", tint = Color.Black)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.Black)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Placeholder content
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        "Spending Analytics Coming Soon",
                        color = Color.Black,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Track and analyze your spending patterns with AI-powered insights",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Preview cards for future features
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FeatureCard(
                                title = "Message Analysis",
                                description = "Automatically categorize spending from your messages",
                                backgroundColor = Color(0xFFF9A691)
                            )
                        }
                        item {
                            FeatureCard(
                                title = "Spending Insights",
                                description = "Get AI-powered insights on your spending habits",
                                backgroundColor = Color(0xFFCDEEF0)
                            )
                        }
                        item {
                            FeatureCard(
                                title = "Budget Tracking",
                                description = "Set budgets and track your progress",
                                backgroundColor = Color(0xFFF6E8B6)
                            )
                        }
                        item {
                            FeatureCard(
                                title = "AI Chat Assistant",
                                description = "Chat with your spending data using AI",
                                backgroundColor = Color(0xFFEAF7F4)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
            )
        }
    }
}