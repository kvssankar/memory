package com.google.ai.edge.gallery.data.notes

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private const val DB_NAME = "notes.db"
private const val DB_VERSION = 1
private const val TABLE_NOTES = "notes"

/** Simple SQLite helper to store notes locally. */
class NotesDatabaseHelper(context: Context) :
  SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS $TABLE_NOTES (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        type TEXT NOT NULL,
        title TEXT NOT NULL,
        tags TEXT NOT NULL,
        description TEXT NOT NULL,
        ai_description TEXT NOT NULL,
        image_path TEXT,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )
      """
        .trimIndent()
    )
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // For now, drop and recreate on upgrade.
    db.execSQL("DROP TABLE IF EXISTS $TABLE_NOTES")
    onCreate(db)
  }

  fun insert(note: Note): Long {
    val values = ContentValues().apply {
      put("type", note.type)
      put("title", note.title)
      put("tags", note.tags.joinToString(","))
      put("description", note.description)
      put("ai_description", note.aiDescription)
      put("image_path", note.imagePath)
      put("created_at", note.createdAt)
      put("updated_at", note.updatedAt)
    }
    return writableDatabase.insert(TABLE_NOTES, null, values)
  }

  fun listAll(): List<Note> {
    val notes = mutableListOf<Note>()
    val cursor: Cursor =
      readableDatabase.query(
        TABLE_NOTES,
        null,
        null,
        null,
        null,
        null,
        "updated_at DESC, created_at DESC"
      )
    cursor.use { c ->
      while (c.moveToNext()) {
        notes.add(
          Note(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            type = c.getString(c.getColumnIndexOrThrow("type")),
            title = c.getString(c.getColumnIndexOrThrow("title")),
            tags = c
              .getString(c.getColumnIndexOrThrow("tags"))
              .split(',')
              .map { it.trim() }
              .filter { it.isNotEmpty() },
            description = c.getString(c.getColumnIndexOrThrow("description")),
            aiDescription = c.getString(c.getColumnIndexOrThrow("ai_description")),
            imagePath = c.getString(c.getColumnIndexOrThrow("image_path")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
          )
        )
      }
    }
    return notes
  }
}

class NotesRepository(private val db: NotesDatabaseHelper) {
  fun add(note: Note): Long = db.insert(note)
  fun all(): List<Note> = db.listAll()
}
