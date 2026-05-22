package com.example.data

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = NoteRepository(
        database.noteDao(),
        database.attachmentDao(),
        application
    )

    // Search query state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Selected category filter pill
    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    // Screen state
    private val _viewingRecycleBin = MutableStateFlow(false)
    val viewingRecycleBin = _viewingRecycleBin.asStateFlow()

    // Notes list state, filtered by search and category
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeNotes: StateFlow<List<Note>> = combine(
        repository.activeNotes,
        _searchQuery,
        _selectedCategory,
        database.attachmentDao().getActiveAttachments()
    ) { notes, query, category, attachments ->
        var filtered = notes

        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true)
            }
        }

        when (category) {
            "Images" -> {
                val notesWithImages = attachments
                    .filter { it.mimeType.startsWith("image/", ignoreCase = true) }
                    .map { it.noteId }
                    .toSet()
                filtered = filtered.filter { it.id in notesWithImages }
            }
            "Files" -> {
                val notesWithFiles = attachments
                    .filter { !it.mimeType.startsWith("image/", ignoreCase = true) }
                    .map { it.noteId }
                    .toSet()
                filtered = filtered.filter { it.id in notesWithFiles }
            }
            "Work" -> {
                filtered = filtered.filter {
                    it.title.contains("work", ignoreCase = true) ||
                            it.content.contains("work", ignoreCase = true) ||
                            it.title.contains("project", ignoreCase = true) ||
                            it.colorArgb == 0xFF1E283C.toInt() || // Cobalt/Work
                            it.colorArgb == 0xFF2A1E3C.toInt() // Violet/Design
                }
            }
        }
        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedNotes: StateFlow<List<Note>> = repository.deletedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedAttachments: StateFlow<List<Attachment>> = repository.deletedAttachments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // State of the selected note for editing/viewing details
    private val _selectedNote = MutableStateFlow<Note?>(null)
    val selectedNote = _selectedNote.asStateFlow()

    // State of attachments for the currently selected note (if any)
    @OptIn(ExperimentalCoroutinesApi::class)
    val noteAttachments: StateFlow<List<Attachment>> = _selectedNote
        .flatMapLatest { note ->
            if (note == null) {
                flowOf(emptyList())
            } else {
                repository.getAttachmentsForNote(note.id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All active attachments (to display files in widget list or attachments-only filter)
    val activeAttachments: Flow<List<Attachment>> = database.attachmentDao().getActiveAttachments()

    // Active Edit State for fields when creating or editing a note
    val editTitle = MutableStateFlow("")
    val editContent = MutableStateFlow("")
    val editColor = MutableStateFlow(0xFF1A1A1A.toInt()) // Default dark charcoal

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setViewingRecycleBin(view: Boolean) {
        _viewingRecycleBin.value = view
    }

    fun selectNoteForEdit(note: Note?) {
        _selectedNote.value = note
        if (note != null) {
            editTitle.value = note.title
            editContent.value = note.content
            editColor.value = note.colorArgb
        } else {
            editTitle.value = ""
            editContent.value = ""
            editColor.value = 0xFF1A1A1A.toInt() // default
        }
    }

    fun saveCurrentNote(onComplete: () -> Unit = {}) {
        val titleVal = editTitle.value.trim()
        val contentVal = editContent.value.trim()
        val colorVal = editColor.value

        // Ignore if both are completely empty
        if (titleVal.isEmpty() && contentVal.isEmpty()) {
            selectNoteForEdit(null)
            onComplete()
            return
        }

        val note = _selectedNote.value
        viewModelScope.launch {
            if (note == null) {
                // Create new
                repository.createNote(titleVal, contentVal, colorVal)
            } else {
                // Update existing
                val updatedNote = note.copy(
                    title = titleVal,
                    content = contentVal,
                    colorArgb = colorVal,
                    timestamp = System.currentTimeMillis() // Touch timestamp
                )
                repository.updateNote(updatedNote)
            }
            selectNoteForEdit(null)
            onComplete()
        }
    }

    fun deleteNoteToRecycleBin(note: Note) {
        viewModelScope.launch {
            repository.moveToRecycleBin(note)
            if (_selectedNote.value?.id == note.id) {
                selectNoteForEdit(null)
            }
        }
    }

    fun restoreNoteFromRecycleBin(note: Note) {
        viewModelScope.launch {
            repository.restoreNote(note)
        }
    }

    fun permanentlyDeleteNote(note: Note) {
        viewModelScope.launch {
            repository.permanentlyDeleteNote(note)
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            repository.emptyRecycleBin()
        }
    }

    // Attachment Actions
    fun addAttachmentToCurrentNote(uri: Uri) {
        val note = _selectedNote.value
        viewModelScope.launch {
            if (note == null) {
                // Force save current session as a draft first to get a real ID!
                val titleVal = editTitle.value.ifBlank { "Untitled Attachment Note" }
                val contentVal = editContent.value
                val colorVal = editColor.value
                val newId = repository.createNote(titleVal, contentVal, colorVal)
                val newNote = database.noteDao().getNoteById(newId)
                if (newNote != null) {
                    _selectedNote.value = newNote
                    repository.addAttachmentToNote(newId, uri)
                }
            } else {
                repository.addAttachmentToNote(note.id, uri)
            }
        }
    }

    fun removeAttachmentToRecycleBin(attachment: Attachment) {
        viewModelScope.launch {
            repository.moveToRecycleBin(attachment)
        }
    }

    fun restoreAttachmentFromRecycleBin(attachment: Attachment) {
        viewModelScope.launch {
            repository.restoreAttachment(attachment)
        }
    }

    fun permanentlyDeleteAttachment(attachment: Attachment) {
        viewModelScope.launch {
            repository.permanentlyDeleteAttachment(attachment)
        }
    }
}
