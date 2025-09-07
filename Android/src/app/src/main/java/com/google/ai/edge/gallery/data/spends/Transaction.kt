package com.google.ai.edge.gallery.data.spends

data class Transaction(
    val id: Long = 0,
    val source: String,
    val target: String,
    val amount: Double,
    val dateOfTransaction: Long, // timestamp
    val type: TransactionType,
    val mode: TransactionMode,
    val category: TransactionCategory,
    val otherInfo: String,
    val originalMessage: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class TransactionType {
    DEBIT, CREDIT
}

enum class TransactionMode {
    CARD, UPI
}

enum class TransactionCategory {
    SHOPPING, FOOD, ENTERTAINMENT, LOANS, TRANSPORT, OTHER
}

data class ProcessingStatus(
    val totalMessages: Int = 0,
    val processedMessages: Int = 0,
    val detectedTransactions: Int = 0,
    val isProcessing: Boolean = false
)