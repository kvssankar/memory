package com.google.ai.edge.gallery.ui.spends

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.spends.*
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AGSpendsViewModel"

@HiltViewModel
class SpendsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {
    
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()
    
    private val _processingStatus = MutableStateFlow(ProcessingStatus())
    val processingStatus: StateFlow<ProcessingStatus> = _processingStatus.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _summary = MutableStateFlow<Map<String, Any>>(emptyMap())
    val summary: StateFlow<Map<String, Any>> = _summary.asStateFlow()
    
    private var smsProcessor: SmsProcessor? = null
    
    fun initialize(context: Context) {
        if (smsProcessor == null) {
            smsProcessor = SmsProcessor(context, transactionRepository)
        }
        loadTransactions()
        loadSummary()
    }
    
    fun startProcessingSms(
        context: Context,
        modelManagerViewModel: ModelManagerViewModel? = null
    ) {
        val processor = smsProcessor ?: SmsProcessor(context, transactionRepository).also { smsProcessor = it }
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Collect processing status
                launch {
                    processor.processingStatus.collect { status ->
                        _processingStatus.value = status
                    }
                }
                
                // Start processing
                processor.processSmsMessages(modelManagerViewModel)
                
                // Refresh data after processing
                loadTransactions()
                loadSummary()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS messages", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun loadTransactions() {
        viewModelScope.launch(Dispatchers.IO) {
            val allTransactions = transactionRepository.all()
            withContext(Dispatchers.Main) {
                _transactions.value = allTransactions
            }
        }
    }
    
    fun loadSummary() {
        viewModelScope.launch(Dispatchers.IO) {
            val summaryData = transactionRepository.getSummary()
            withContext(Dispatchers.Main) {
                _summary.value = summaryData
            }
        }
    }
    
    fun clearAllTransactions() {
        viewModelScope.launch(Dispatchers.IO) {
            transactionRepository.clearAll()
            smsProcessor?.resetProcessing()
            withContext(Dispatchers.Main) {
                _transactions.value = emptyList()
                _summary.value = emptyMap()
                _processingStatus.value = ProcessingStatus()
            }
        }
    }
}