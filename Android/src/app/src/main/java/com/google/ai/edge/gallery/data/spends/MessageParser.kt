package com.google.ai.edge.gallery.data.spends

import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class MessageParser {
    
    companion object {
        private val AMOUNT_PATTERNS = listOf(
            Pattern.compile("(?:INR|Rs\\.?|₹)\\s*(\\d+(?:,\\d{3})*(?:\\.\\d{2})?)"),
            Pattern.compile("debited for INR\\s*(\\d+(?:,\\d{3})*(?:\\.\\d{2})?)"),
            Pattern.compile("credited with INR\\s*(\\d+(?:,\\d{3})*(?:\\.\\d{2})?)"),
            Pattern.compile("amount\\s*(?:INR|Rs\\.?|₹)?\\s*(\\d+(?:,\\d{3})*(?:\\.\\d{2})?)"),
        )
        
        private val DATE_PATTERNS = listOf(
            Pattern.compile("on\\s*(\\d{1,2})-(\\w{3})-(\\d{2,4})"),
            Pattern.compile("on\\s*(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})"),
            Pattern.compile("dated\\s*(\\d{1,2})-(\\w{3})-(\\d{2,4})"),
            Pattern.compile("(\\d{1,2})-(\\w{3})-(\\d{2,4})"),
        )
        
        private val BANK_PATTERNS = mapOf(
            "ICICI" to listOf("ICICI", "ICICI Bank"),
            "HDFC" to listOf("HDFC", "HDFC Bank"),
            "SBI" to listOf("SBI", "State Bank"),
            "AXIS" to listOf("AXIS", "Axis Bank"),
            "KOTAK" to listOf("KOTAK", "Kotak"),
            "PNB" to listOf("PNB", "Punjab National"),
            "BOB" to listOf("BOB", "Bank of Baroda"),
            "CANARA" to listOf("CANARA", "Canara Bank"),
        )
        
        private val FOOD_KEYWORDS = listOf("zomato", "swiggy", "restaurant", "food", "cafe", "hotel", "domino", "mcdonald", "kfc", "pizza")
        private val SHOPPING_KEYWORDS = listOf("amazon", "flipkart", "myntra", "ajio", "shopping", "mall", "store", "market")
        private val TRANSPORT_KEYWORDS = listOf("uber", "ola", "rapido", "metro", "bus", "taxi", "petrol", "diesel", "fuel")
        private val ENTERTAINMENT_KEYWORDS = listOf("netflix", "hotstar", "prime", "spotify", "movie", "cinema", "pvr", "inox")
        private val UTILITY_KEYWORDS = listOf("electricity", "water", "gas", "internet", "mobile", "recharge", "bill")
    }
    
    fun parseMessage(message: String): Transaction? {
        if (!isTransactionMessage(message)) {
            return null
        }
        
        val amount = extractAmount(message) ?: return null
        val dateOfTransaction = extractDate(message) ?: System.currentTimeMillis()
        val source = extractSource(message)
        val target = extractTarget(message)
        val type = extractTransactionType(message)
        val mode = extractTransactionMode(message)
        val category = categorizeTransaction(message, target)
        val otherInfo = extractOtherInfo(message)
        
        return Transaction(
            source = source,
            target = target,
            amount = amount,
            dateOfTransaction = dateOfTransaction,
            type = type,
            mode = mode,
            category = category,
            otherInfo = otherInfo,
            originalMessage = message
        )
    }
    
    private fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return (lowerMessage.contains("debited") || lowerMessage.contains("credited")) &&
               (lowerMessage.contains("inr") || lowerMessage.contains("rs") || lowerMessage.contains("₹")) &&
               (BANK_PATTERNS.values.flatten().any { bank -> lowerMessage.contains(bank.lowercase()) })
    }
    
    private fun extractAmount(message: String): Double? {
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val amountStr = matcher.group(1)?.replace(",", "") ?: continue
                try {
                    return amountStr.toDouble()
                } catch (e: NumberFormatException) {
                    continue
                }
            }
        }
        return null
    }
    
    private fun extractDate(message: String): Long? {
        for (pattern in DATE_PATTERNS) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                try {
                    val day = matcher.group(1)?.toInt() ?: continue
                    val monthOrDay = matcher.group(2) ?: continue
                    val year = matcher.group(3)?.let { if (it.length == 2) "20$it" else it }?.toInt() ?: continue
                    
                    val calendar = Calendar.getInstance()
                    
                    if (monthOrDay.length == 3) {
                        val monthMap = mapOf(
                            "jan" to Calendar.JANUARY, "feb" to Calendar.FEBRUARY, "mar" to Calendar.MARCH,
                            "apr" to Calendar.APRIL, "may" to Calendar.MAY, "jun" to Calendar.JUNE,
                            "jul" to Calendar.JULY, "aug" to Calendar.AUGUST, "sep" to Calendar.SEPTEMBER,
                            "oct" to Calendar.OCTOBER, "nov" to Calendar.NOVEMBER, "dec" to Calendar.DECEMBER
                        )
                        val month = monthMap[monthOrDay.lowercase()] ?: continue
                        calendar.set(year, month, day)
                    } else {
                        val month = monthOrDay.toInt() - 1 // Calendar month is 0-based
                        calendar.set(year, month, day)
                    }
                    
                    return calendar.timeInMillis
                } catch (e: Exception) {
                    continue
                }
            }
        }
        return null
    }
    
    private fun extractSource(message: String): String {
        val lowerMessage = message.lowercase()
        for ((bankCode, bankNames) in BANK_PATTERNS) {
            for (bankName in bankNames) {
                if (lowerMessage.contains(bankName.lowercase())) {
                    val cardPattern = Pattern.compile("${bankName}.*?(\\w{2}\\d{4})", Pattern.CASE_INSENSITIVE)
                    val matcher = cardPattern.matcher(message)
                    if (matcher.find()) {
                        return "$bankName ${matcher.group(1)}"
                    }
                    return bankName
                }
            }
        }
        return "Unknown Bank"
    }
    
    private fun extractTarget(message: String): String {
        val lowerMessage = message.lowercase()
        
        // Look for common patterns to identify target
        val patterns = listOf(
            // "for Merchant." or "for Merchant "
            Pattern.compile("for\\s+([A-Za-z0-9\\s&'-]+)(?:\\.|\\s+(?:on|available|to|upi|sms))", Pattern.CASE_INSENSITIVE),
            // "at Merchant." or "at Merchant "
            Pattern.compile("at\\s+([A-Za-z0-9\\s&'-]+)(?:\\.|\\s+(?:on|available|to|upi|sms))", Pattern.CASE_INSENSITIVE),
            // "to Merchant on" (for UPI payments)
            Pattern.compile("to\\s+([A-Za-z0-9\\s&'-]+)\\s+on", Pattern.CASE_INSENSITIVE),
            // "Payment to Merchant"
            Pattern.compile("payment\\s+(?:of\\s+[\\d,.]+\\s+)?to\\s+([A-Za-z0-9\\s&'-]+)(?:\\s+on|\\.|$)", Pattern.CASE_INSENSITIVE),
            // Generic merchant extraction after amount patterns
            Pattern.compile("(?:inr|rs\\.?)\\s*[\\d,.]+\\s+(?:on\\s+[\\d-]+\\s+)?(?:for\\s+|to\\s+|at\\s+)?([A-Za-z0-9\\s&'-]+?)(?:\\s*\\.|\\s+(?:on|available|to|upi|sms|thank))", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val target = matcher.group(1)?.trim() ?: ""
                if (isValidTarget(target)) {
                    return target
                }
            }
        }
        
        return "Unknown"
    }
    
    private fun isValidTarget(target: String): Boolean {
        if (target.isEmpty() || target.length < 2) return false
        val lowerTarget = target.lowercase()
        
        // Exclude common non-merchant words
        val excludeWords = listOf(
            "inr", "rs", "available", "balance", "account", "card", "bank", 
            "credit", "debit", "payment", "transaction", "sms", "call", "dispute",
            "thank", "you", "banking", "with", "us", "ref", "upi", "gpay"
        )
        
        // Check if target is just excluded words
        val words = lowerTarget.split("\\s+".toRegex())
        val validWords = words.filter { word -> 
            word.isNotEmpty() && !excludeWords.contains(word) && !word.matches("\\d+".toRegex())
        }
        
        return validWords.isNotEmpty() && validWords.joinToString(" ").length >= 2
    }
    
    private fun extractTransactionType(message: String): TransactionType {
        val lowerMessage = message.lowercase()
        return if (lowerMessage.contains("debited") || lowerMessage.contains("debit")) {
            TransactionType.DEBIT
        } else {
            TransactionType.CREDIT
        }
    }
    
    private fun extractTransactionMode(message: String): TransactionMode {
        val lowerMessage = message.lowercase()
        return if (lowerMessage.contains("upi") || lowerMessage.contains("gpay") || 
                   lowerMessage.contains("paytm") || lowerMessage.contains("phonepe")) {
            TransactionMode.UPI
        } else {
            TransactionMode.CARD
        }
    }
    
    private fun categorizeTransaction(message: String, target: String): TransactionCategory {
        val lowerMessage = message.lowercase()
        val lowerTarget = target.lowercase()
        
        when {
            FOOD_KEYWORDS.any { lowerMessage.contains(it) || lowerTarget.contains(it) } -> return TransactionCategory.FOOD
            SHOPPING_KEYWORDS.any { lowerMessage.contains(it) || lowerTarget.contains(it) } -> return TransactionCategory.SHOPPING
            TRANSPORT_KEYWORDS.any { lowerMessage.contains(it) || lowerTarget.contains(it) } -> return TransactionCategory.TRANSPORT
            ENTERTAINMENT_KEYWORDS.any { lowerMessage.contains(it) || lowerTarget.contains(it) } -> return TransactionCategory.ENTERTAINMENT
            UTILITY_KEYWORDS.any { lowerMessage.contains(it) || lowerTarget.contains(it) } -> return TransactionCategory.OTHER
        }
        
        return TransactionCategory.OTHER
    }
    
    private fun extractOtherInfo(message: String): String {
        val patterns = listOf(
            "To dispute call\\s+([\\d\\s/-]+)",
            "SMS\\s+([A-Z]+)\\s+\\d+\\s+to\\s+([\\d]+)",
            "call\\s+([\\d\\s/-]+)",
            "helpline\\s+([\\d\\s/-]+)"
        )
        
        for (pattern in patterns) {
            val matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(message)
            if (matcher.find()) {
                return matcher.group(0) ?: ""
            }
        }
        
        return ""
    }
}