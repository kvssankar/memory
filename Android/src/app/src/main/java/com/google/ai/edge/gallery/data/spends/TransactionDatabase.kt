package com.google.ai.edge.gallery.data.spends

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.*

private const val DB_NAME = "transactions.db"
private const val DB_VERSION = 2
private const val TABLE_TRANSACTIONS = "transactions"

class TransactionDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TRANSACTIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                target TEXT NOT NULL,
                amount REAL NOT NULL,
                date_of_transaction INTEGER NOT NULL,
                type TEXT NOT NULL,
                mode TEXT NOT NULL,
                category TEXT NOT NULL,
                other_info TEXT NOT NULL,
                original_message TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Migrate SALARY category to OTHER
            db.execSQL("UPDATE $TABLE_TRANSACTIONS SET category = 'OTHER' WHERE category = 'SALARY'")
        }
    }

    fun insert(transaction: Transaction): Long {
        val values = ContentValues().apply {
            put("source", transaction.source)
            put("target", transaction.target)
            put("amount", transaction.amount)
            put("date_of_transaction", transaction.dateOfTransaction)
            put("type", transaction.type.name)
            put("mode", transaction.mode.name)
            put("category", transaction.category.name)
            put("other_info", transaction.otherInfo)
            put("original_message", transaction.originalMessage)
            put("created_at", transaction.createdAt)
            put("updated_at", transaction.updatedAt)
        }
        return writableDatabase.insert(TABLE_TRANSACTIONS, null, values)
    }

    fun listAll(): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        val cursor: Cursor = readableDatabase.query(
            TABLE_TRANSACTIONS,
            null,
            null,
            null,
            null,
            null,
            "date_of_transaction DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                transactions.add(
                    Transaction(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        source = c.getString(c.getColumnIndexOrThrow("source")),
                        target = c.getString(c.getColumnIndexOrThrow("target")),
                        amount = c.getDouble(c.getColumnIndexOrThrow("amount")),
                        dateOfTransaction = c.getLong(c.getColumnIndexOrThrow("date_of_transaction")),
                        type = TransactionType.valueOf(c.getString(c.getColumnIndexOrThrow("type"))),
                        mode = TransactionMode.valueOf(c.getString(c.getColumnIndexOrThrow("mode"))),
                        category = try { 
                            TransactionCategory.valueOf(c.getString(c.getColumnIndexOrThrow("category"))) 
                        } catch (e: IllegalArgumentException) { 
                            // Handle old SALARY category by converting to OTHER
                            TransactionCategory.OTHER 
                        },
                        otherInfo = c.getString(c.getColumnIndexOrThrow("other_info")),
                        originalMessage = c.getString(c.getColumnIndexOrThrow("original_message")),
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
                    )
                )
            }
        }
        return transactions
    }

    fun getTransactionsSummary(): Map<String, Any> {
        val summary = mutableMapOf<String, Any>()
        
        val cursor = readableDatabase.rawQuery(
            """
            SELECT 
                COUNT(*) as total_transactions,
                SUM(CASE WHEN type = 'DEBIT' THEN amount ELSE 0 END) as total_debits,
                SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE 0 END) as total_credits,
                COUNT(DISTINCT category) as categories_count
            FROM $TABLE_TRANSACTIONS
            """.trimIndent(),
            null
        )
        
        cursor.use { c ->
            if (c.moveToFirst()) {
                summary["total_transactions"] = c.getInt(0)
                summary["total_debits"] = c.getDouble(1)
                summary["total_credits"] = c.getDouble(2)
                summary["categories_count"] = c.getInt(3)
            }
        }
        return summary
    }

    fun clearAll() {
        writableDatabase.delete(TABLE_TRANSACTIONS, null, null)
    }
}

class TransactionRepository(private val db: TransactionDatabaseHelper) {
    fun add(transaction: Transaction): Long = db.insert(transaction)
    fun all(): List<Transaction> = db.listAll()
    fun getSummary(): Map<String, Any> = db.getTransactionsSummary()
    fun clearAll() = db.clearAll()
    
    fun runSelect(sql: String): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        val cursor = db.readableDatabase.rawQuery(sql, null)
        cursor.use { c ->
            while (c.moveToNext()) {
                val row = mutableMapOf<String, Any?>()
                for (i in 0 until c.columnCount) {
                    val columnName = c.getColumnName(i)
                    val value = when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        Cursor.FIELD_TYPE_STRING -> c.getString(i)
                        Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                        else -> c.getString(i)
                    }
                    row[columnName] = value
                }
                results.add(row)
            }
        }
        return results
    }
}