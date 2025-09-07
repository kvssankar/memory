package com.google.ai.edge.gallery.data.spends

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.*

private const val TAG = "AGSmsProcessor"

class SmsProcessor(
    private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val messageParser: MessageParser = MessageParser()
) {
    
    private val _processingStatus = MutableStateFlow(ProcessingStatus())
    val processingStatus: StateFlow<ProcessingStatus> = _processingStatus.asStateFlow()
    
    // Sample SMS messages for demo purposes
    private val sampleMessages = listOf(
        "ICICI Bank Credit Card XX7004 debited for INR 624.00 on 26-Aug-25 for Satguru. To dispute call 18001080/SMS BLOCK 7004 to 9215676766",
        "HDFC Bank: Your account XX1234 is debited for INR 150.00 on 25-Aug-25 for Zomato. Available balance: INR 25000.00",
        "BOB UPI: Payment of INR 350.00 to BigBasket on 24-Aug-25. UPI Ref: 712345678904",
        "AXIS Bank UPI: Payment of INR 45.00 to Uber on 23-Aug-25. UPI Ref: 412345678901",
        "KOTAK Bank: Your account debited for INR 899.00 on 22-Aug-25 for Amazon. Available balance: INR 15000.00",
        "PNB Credit Card XX9012 debited for INR 1200.00 on 21-Aug-25 for PVR Cinemas. To dispute call 1800118001",
        "ICICI Bank UPI: Payment of INR 67.50 to Swiggy on 20-Aug-25. UPI Ref: 512345678902",
        "HDFC Bank: Your account XX1234 is debited for INR 3500.00 on 19-Aug-25 for Flipkart. Available balance: INR 21500.00",
        "SBI: UPI payment of INR 25.00 to Metro Card on 18-Aug-25. Balance: INR 8000.00",
        "AXIS Bank Credit Card XX3456 debited for INR 299.00 on 17-Aug-25 for Netflix. Minimum due: INR 5000.00",
        "KOTAK Bank: Your account debited for INR 1800.00 on 16-Aug-25 for Electricity Bill. Available balance: INR 13200.00",
        "ICICI Bank: UPI payment of INR 180.00 to PhonePe on 15-Aug-25 for Mobile Recharge. UPI Ref: 612345678903",
        "HDFC Bank Credit Card XX7890 debited for INR 750.00 on 14-Aug-25 for McDonald's. To dispute call 18002022"
    )
    
    suspend fun processSmsMessages(modelManagerViewModel: ModelManagerViewModel? = null) {
        try {
            _processingStatus.value = _processingStatus.value.copy(
                isProcessing = true,
                totalMessages = sampleMessages.size,
                processedMessages = 0,
                detectedTransactions = 0
            )
            
            // Clear existing transactions for demo
            transactionRepository.clearAll()
            
            var detectedCount = 0
            var model: Model? = null
            var task: Task? = null
            
            // Try to get LLM model for enhanced parsing
            if (modelManagerViewModel != null) {
                val (pickedModel, pickedTask) = pickAvailableLlmModel(modelManagerViewModel) ?: (null to null)
                if (pickedModel != null && pickedTask != null) {
                    // Configure model to use GPU for better performance
                    pickedModel.configValues = pickedModel.configValues.toMutableMap().apply {
                        put("Choose accelerator", "GPU")
                    }
                    
                    if (ensureModelInitializedAsync(context, modelManagerViewModel, pickedTask, pickedModel)) {
                        model = pickedModel
                        task = pickedTask
                    }
                }
            }
            
            // Process messages in parallel batches of 10
            val batchSize = 10
            val batches = sampleMessages.chunked(batchSize)
            
            batches.forEachIndexed { batchIndex, batch ->
                // Process current batch in parallel using coroutineScope
                val batchResults = coroutineScope {
                    batch.map { message ->
                        async(Dispatchers.Default) {
                            // Add small delay to prevent overwhelming the system
                            delay((50..200).random().toLong())
                            
                            val transaction = if (model != null) {
                                // Use LLM for parsing
                                parseSmsWithLlm(message, model)
                            } else {
                                // Fallback to regex parsing only if no LLM available
                                messageParser.parseMessage(message)
                            }
                            transaction
                        }
                    }.awaitAll()
                }
                
                // Add successful transactions to database
                batchResults.forEach { transaction ->
                    if (transaction != null) {
                        transactionRepository.add(transaction)
                        detectedCount++
                    }
                }
                
                // Update progress after each batch
                val processedCount = ((batchIndex + 1) * batchSize).coerceAtMost(sampleMessages.size)
                _processingStatus.value = _processingStatus.value.copy(
                    processedMessages = processedCount,
                    detectedTransactions = detectedCount
                )
                
                // Small delay between batches to show progress
                delay(200)
            }
            
            _processingStatus.value = _processingStatus.value.copy(isProcessing = false)
            
        } catch (e: Exception) {
            _processingStatus.value = _processingStatus.value.copy(
                isProcessing = false
            )
            throw e
        }
    }
    
    // Future implementation for reading actual SMS
    private fun readActualSmsMessages(): List<String> {
        val messages = mutableListOf<String>()
        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("address", "body", "date")
        
        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC LIMIT 100"
            )
            
            cursor?.use { c ->
                val bodyIndex = c.getColumnIndexOrThrow("body")
                while (c.moveToNext()) {
                    val body = c.getString(bodyIndex)
                    if (body != null) {
                        messages.add(body)
                    }
                }
            }
        } catch (e: Exception) {
            // Handle permission or other errors
            // For now, fall back to sample data
        }
        
        return messages.ifEmpty { sampleMessages }
    }
    
    fun resetProcessing() {
        _processingStatus.value = ProcessingStatus()
    }
    
    private suspend fun parseSmsWithLlm(message: String, model: Model): Transaction? {
        try {
            val prompt = buildTransactionPrompt(message)
            val result = runLlm(prompt, model)
            val transaction = parseTransactionResult(result, message)
            
            // If LLM parsing fails, try regex as absolute fallback
            return transaction ?: messageParser.parseMessage(message)
        } catch (e: Exception) {
            Log.e(TAG, "LLM parsing failed for message, falling back to regex", e)
            return messageParser.parseMessage(message)
        }
    }
    
    private fun buildTransactionPrompt(message: String): String {
        return """
            You are an expert at parsing banking SMS messages into structured transaction data.
            
            First, determine if this SMS is actually a banking transaction message (contains debit/credit with amount and merchant).
            If it's NOT a transaction (like OTP, promotional message, etc.), return: {"is_transaction": false}
            
            If it IS a transaction, parse and extract the details. Return a JSON object with these exact keys:
            - is_transaction: true
            - source: The bank/source (e.g., "ICICI Bank Credit Card XX7004")
            - target: The merchant/business name where money was spent. Extract the actual merchant name, not "Unknown". Examples: "Satguru", "Amazon", "Zomato", "BigBasket", "PVR Cinemas", "Netflix", "Uber", "Metro Card", "PhonePe", "McDonald's"
            - amount: Numeric amount only (e.g., 624.00)
            - date_of_transaction: Unix timestamp in milliseconds for the transaction date
            - type: Either "DEBIT" or "CREDIT" 
            - mode: Either "CARD" or "UPI"
            - category: One of "SHOPPING", "FOOD", "ENTERTAINMENT", "LOANS", "TRANSPORT", "OTHER"
            - other_info: Any additional info like dispute numbers or reference IDs
            
            IMPORTANT: For the target field, carefully identify the merchant name from phrases like:
            - "for [MERCHANT]" → extract MERCHANT
            - "to [MERCHANT]" → extract MERCHANT  
            - "at [MERCHANT]" → extract MERCHANT
            - "Payment to [MERCHANT]" → extract MERCHANT
            Do NOT return "Unknown" unless absolutely no merchant can be identified.
            
            Categories guide:
            - FOOD: Zomato, Swiggy, restaurants, cafes, food delivery
            - SHOPPING: Amazon, Flipkart, BigBasket, retail stores, e-commerce
            - TRANSPORT: Uber, Ola, fuel, metro, taxi
            - ENTERTAINMENT: Netflix, movies, streaming services, PVR
            - LOANS: EMIs, loan payments, credit payments
            - OTHER: Utilities, bills, salary credits, PhonePe, unknown merchants
            
            SMS Message: "${message.replace("\"", "'")}"
            
            Output only valid JSON, no other text:
        """.trimIndent()
    }
    
    private fun parseTransactionResult(result: String, originalMessage: String): Transaction? {
        val jsonText = sanitizeToJson(result)
        return try {
            val obj = JSONObject(jsonText)
            
            // Check if LLM determined this is a transaction
            val isTransaction = obj.optBoolean("is_transaction", true)
            if (!isTransaction) {
                return null
            }
            
            val source = obj.optString("source", "Unknown Bank")
            val target = obj.optString("target", "Unknown")
            val amount = obj.optDouble("amount", 0.0)
            val dateTimestamp = obj.optLong("date_of_transaction", System.currentTimeMillis())
            val typeStr = obj.optString("type", "DEBIT")
            val modeStr = obj.optString("mode", "CARD")
            val categoryStr = obj.optString("category", "OTHER")
            val otherInfo = obj.optString("other_info", "")
            
            if (amount <= 0) return null
            
            Transaction(
                source = source,
                target = target,
                amount = amount,
                dateOfTransaction = dateTimestamp,
                type = try { TransactionType.valueOf(typeStr) } catch (e: Exception) { TransactionType.DEBIT },
                mode = try { TransactionMode.valueOf(modeStr) } catch (e: Exception) { TransactionMode.CARD },
                category = try { TransactionCategory.valueOf(categoryStr) } catch (e: Exception) { TransactionCategory.OTHER },
                otherInfo = otherInfo,
                originalMessage = originalMessage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse LLM transaction result", e)
            null
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
    
    private fun pickAvailableLlmModel(modelManagerViewModel: ModelManagerViewModel): Pair<Model, Task>? {
        val ui = modelManagerViewModel.uiState.value
        val tasks = ui.tasks
        val llmTasks = tasks.filter { 
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
            return true
        }

        return try {
            modelManagerViewModel.initializeModel(context, task, model)
            // Wait a bit for initialization
            kotlinx.coroutines.delay(2000)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Model initialization failed", e)
            false
        }
    }
    
    private fun sanitizeToJson(text: String): String {
        val normalized = text.trim()
        val codeFenceRegex = Regex("```[a-zA-Z]*")
        val noFences = normalized.replace(codeFenceRegex, "").replace("```", "").trim()
        val start = noFences.indexOf('{')
        val end = noFences.lastIndexOf('}')
        return if (start >= 0 && end > start) noFences.substring(start, end + 1) else noFences
    }
    
}