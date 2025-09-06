package com.google.ai.edge.gallery.data.notes

data class Note(
  val id: Long = 0L,
  val type: String, // "text" or "image"
  val title: String,
  val tags: List<String>,
  val description: String,
  val aiDescription: String,
  val imagePath: String? = null,
  val createdAt: Long,
  val updatedAt: Long,
)

