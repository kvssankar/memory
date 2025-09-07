package com.google.ai.edge.gallery.ui.spends

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.spends.*
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendsScreen(
    onOpenSettings: () -> Unit = {},
    onOpenSpendChat: () -> Unit = {},
    modelManagerViewModel: ModelManagerViewModel? = null,
    viewModel: SpendsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val transactions by viewModel.transactions.collectAsState()
    val processingStatus by viewModel.processingStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val summary by viewModel.summary.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
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
        floatingActionButton = {
            if (transactions.isEmpty() && !isLoading && !processingStatus.isProcessing) {
                FloatingActionButton(
                    onClick = { viewModel.startProcessingSms(context, modelManagerViewModel) },
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Rounded.Analytics, contentDescription = "Process Messages")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            when {
                processingStatus.isProcessing -> {
                    ProcessingView(processingStatus)
                }
                transactions.isNotEmpty() -> {
                    TransactionsView(transactions, summary) {
                        viewModel.clearAllTransactions()
                    }
                }
                else -> {
                    EmptyStateView()
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Analytics,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Ready to Analyze Your Spending",
                color = Color.Black,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Tap the button below to process your SMS messages and discover your spending patterns with AI",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProcessingView(status: ProcessingStatus) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Analytics,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier
                    .size(80.dp)
                    .rotate(rotationAngle)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Processing SMS Messages",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    StatusRow("Total Messages", "${status.totalMessages}")
                    StatusRow("Processed", "${status.processedMessages}")
                    StatusRow("Transactions Found", "${status.detectedTransactions}")
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val progress = if (status.totalMessages > 0) {
                        status.processedMessages.toFloat() / status.totalMessages.toFloat()
                    } else 0f
                    
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

@Composable
private fun TransactionsView(
    transactions: List<Transaction>,
    summary: Map<String, Any>,
    onClearAll: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SummaryCard(summary, onClearAll)
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        items(transactions) { transaction ->
            TransactionCard(transaction)
        }
    }
}

@Composable
private fun SummaryCard(summary: Map<String, Any>, onClearAll: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Transaction Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    "Total",
                    "${summary["total_transactions"] ?: 0}",
                    Color(0xFF2196F3)
                )
                SummaryItem(
                    "Debits",
                    "₹${String.format("%.0f", summary["total_debits"] as? Double ?: 0.0)}",
                    Color(0xFFFF5722)
                )
                SummaryItem(
                    "Credits",
                    "₹${String.format("%.0f", summary["total_credits"] as? Double ?: 0.0)}",
                    Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun TransactionCard(transaction: Transaction) {
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val date = dateFormatter.format(Date(transaction.dateOfTransaction))
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = transaction.target,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = if (transaction.type == TransactionType.DEBIT) "-₹${transaction.amount}" else "+₹${transaction.amount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.type == TransactionType.DEBIT) Color.Red else Color.Green
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = transaction.source,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(transaction.category.name)
                ModeChip(transaction.mode.name)
            }
            
            if (transaction.otherInfo.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = transaction.otherInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(category: String) {
    Surface(
        color = getCategoryColor(category),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = category.lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ModeChip(mode: String) {
    Surface(
        color = Color.Gray,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = mode,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun getCategoryColor(category: String): Color {
    return when (category.uppercase()) {
        "FOOD" -> Color(0xFFFF9800)
        "SHOPPING" -> Color(0xFF9C27B0)
        "TRANSPORT" -> Color(0xFF2196F3)
        "ENTERTAINMENT" -> Color(0xFFE91E63)
        else -> Color(0xFF607D8B)
    }
}