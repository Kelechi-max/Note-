package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Attachment
import com.example.data.Note
import com.example.data.NoteViewModel
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: NoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle initial intent widget triggers
        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    NoteAppScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == NoteWidgetProvider.ACTION_ADD_NOTE) {
            // Trigger Compose's Add Note Composer state instantly
            viewModel.selectNoteForEdit(null)
            viewModel.setViewingRecycleBin(false)
        }
    }
}

@Composable
fun NoteAppScreen(
    viewModel: NoteViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Observe DB States
    val notes by viewModel.activeNotes.collectAsState()
    val deletedNotes by viewModel.deletedNotes.collectAsState()
    val deletedAttachments by viewModel.deletedAttachments.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val viewingRecycleBin by viewModel.viewingRecycleBin.collectAsState()
    val selectedNote by viewModel.selectedNote.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val activeAttachments by viewModel.activeAttachments.collectAsState(initial = emptyList())

    // Screen navigation layout state
    var showWidgetGuide by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Header: iPhone Style bold with custom rotating abstract N branding
            HeaderSection(
                onRecycleBinClick = { viewModel.setViewingRecycleBin(true) },
                onHomeClick = { viewModel.setViewingRecycleBin(false) },
                onWidgetGuideClick = { showWidgetGuide = true },
                viewingRecycleBin = viewingRecycleBin,
                totalNotesCount = notes.size
            )

            // Search Filter Row
            SearchAndFilterSection(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onClearQuery = { viewModel.setSearchQuery("") },
                focusManager = focusManager
            )

            // Quick Filter Pills Row (Only in main view)
            if (!viewingRecycleBin) {
                CategoryFiltersSection(
                    categories = listOf("All", "Images", "Files", "Work"),
                    selectedCategory = selectedCategory,
                    onCategorySelected = { viewModel.setSelectedCategory(it) }
                )
            }

            // Dynamic Main Window Area (Transitioning between Notes Board and Recycle Bin)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Crossfade(
                    targetState = viewingRecycleBin,
                    animationSpec = tween(350),
                    label = "ScreenCrossfade"
                ) { binView ->
                    if (binView) {
                           RecycleBinScreen(
                               deletedNotes = deletedNotes,
                               deletedAttachments = deletedAttachments,
                               onRestoreNote = { viewModel.restoreNoteFromRecycleBin(it) },
                               onDeleteNotePermanently = { viewModel.permanentlyDeleteNote(it) },
                               onRestoreAttachment = { viewModel.restoreAttachmentFromRecycleBin(it) },
                               onDeleteAttachmentPermanently = { viewModel.permanentlyDeleteAttachment(it) },
                               onEmptyBinClick = { viewModel.emptyRecycleBin() }
                           )
                    } else {
                        NotesBoardScreen(
                            notes = notes,
                            activeAttachments = activeAttachments,
                            onNoteSelect = { viewModel.selectNoteForEdit(it) },
                            onNoteDelete = { viewModel.deleteNoteToRecycleBin(it) }
                        )
                    }
                }
            }
        }

        // iPhone-inspired Bottom Action Sheet Note Editor Layout
        AnimatedVisibility(
            visible = (selectedNote != null || viewModel.editTitle.value.isNotEmpty() || viewModel.editContent.value.isNotEmpty()),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
            )
        ) {
            NoteComposerSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.saveCurrentNote() }
            )
        }

        // Material 3 Custom Floating Action Button to launch Note Composer
        AnimatedVisibility(
            visible = !viewingRecycleBin && selectedNote == null && viewModel.editTitle.value.isEmpty() && viewModel.editContent.value.isEmpty(),
            enter = scaleIn(animationSpec = spring(dampingRatio = 0.6f)) + fadeIn(),
            exit = scaleOut(animationSpec = spring(dampingRatio = 0.6f)) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            LargeFloatingActionButton(
                onClick = { viewModel.selectNoteForEdit(null) },
                containerColor = Color.White,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .size(64.dp)
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                    .testTag("add_note_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Note",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Informative custom Widget Guide Sheet
        if (showWidgetGuide) {
            WidgetGuideDialog(
                onDismiss = { showWidgetGuide = false }
            )
        }
    }
}

@Composable
fun HeaderSection(
    onRecycleBinClick: () -> Unit,
    onHomeClick: () -> Unit,
    onWidgetGuideClick: () -> Unit,
    viewingRecycleBin: Boolean,
    totalNotesCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Aesthetic Custom N Emblem matching Sophisticated Dark design
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color.White, shape = RoundedCornerShape(12.dp))
                .clickable { onHomeClick() },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Branding",
                modifier = Modifier.size(34.dp),
                colorFilter = ColorFilter.tint(Color.Black)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Large display typography
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (viewingRecycleBin) "Recycle Bin" else "Notes",
                style = MaterialTheme.typography.displayLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
            )
            Text(
                text = if (viewingRecycleBin) "Deleted items to recover" else "$totalNotesCount active items",
                color = Color(0xFF8E8E93),
                fontSize = 14.sp
            )
        }

        // iPhone Style pill selections
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledIconButton(
                onClick = onWidgetGuideClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF1C1C1E),
                    contentColor = Color.White
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Widgets,
                    contentDescription = "Widget Setup",
                    modifier = Modifier.size(20.dp)
                )
            }

            FilledIconButton(
                onClick = {
                    if (viewingRecycleBin) onHomeClick() else onRecycleBinClick()
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (viewingRecycleBin) Color.White else Color(0xFF1C1C1E),
                    contentColor = if (viewingRecycleBin) Color.Black else Color.White
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (viewingRecycleBin) Icons.Default.Close else Icons.Outlined.Delete,
                    contentDescription = "Recycle Bin",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Infix / operator bypass error
private val Icons.Outlined.Delete: androidx.compose.ui.graphics.vector.ImageVector get() = Icons.Default.Delete

@Composable
fun CategoryFiltersSection(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 20.dp)
    ) {
        items(categories) { category ->
            val isSelected = category == selectedCategory
            Box(
                modifier = Modifier
                    .background(
                        color = if (isSelected) Color.White else Color(0xFF1A1A1A),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color.Transparent else Color(0x0DFFFFFF),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (category == "Files") "PDFs / Files" else category,
                    color = if (isSelected) Color.Black else Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SearchAndFilterSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color(0xFF1C1C1E), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = Color(0xFF8E8E93),
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search titles or tags...",
                        color = Color(0xFF8E8E93),
                        style = TextStyle(fontSize = 16.sp)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClearQuery,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NotesBoardScreen(
    notes: List<Note>,
    activeAttachments: List<Attachment>,
    onNoteSelect: (Note) -> Unit,
    onNoteDelete: (Note) -> Unit
) {
    if (notes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF1C1C1E), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NoteAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "No notes saved yet",
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.White)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap + or use the home widget to record thoughts instantly.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF8E8E93)),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        val noteAttachmentsCountMap = remember(activeAttachments) {
            activeAttachments.groupBy { it.noteId }.mapValues { it.value.size }
        }

        // Modern cascading view of notes arranged inside cards
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(notes, key = { it.id }) { note ->
                val attachmentCount = noteAttachmentsCountMap[note.id] ?: 0
                NoteCard(
                    note = note,
                    attachmentCount = attachmentCount,
                    onClick = { onNoteSelect(note) },
                    onDelete = { onNoteDelete(note) }
                )
            }
        }
    }
}

@Composable
fun NoteCard(
    note: Note,
    attachmentCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val formattedDate = remember(note.timestamp) {
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        sdf.format(Date(note.timestamp))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp, max = 220.dp)
            .background(Color(note.colorArgb), RoundedCornerShape(28.dp))
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(28.dp))
            .clickable { onClick() }
            .padding(18.dp)
            .testTag("note_card_${note.id}")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color(0x19FFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete Note",
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = note.content.ifBlank { "No content" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8)
                ),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = TextStyle(fontSize = 10.sp, color = Color(0xFF64748B)),
                    modifier = Modifier.weight(1f)
                )

                if (attachmentCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(Color(0x0FFFFFFF), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "$attachmentCount",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecycleBinScreen(
    deletedNotes: List<Note>,
    deletedAttachments: List<Attachment>,
    onRestoreNote: (Note) -> Unit,
    onDeleteNotePermanently: (Note) -> Unit,
    onRestoreAttachment: (Attachment) -> Unit,
    onDeleteAttachmentPermanently: (Attachment) -> Unit,
    onEmptyBinClick: () -> Unit
) {
    if (deletedNotes.isEmpty() && deletedAttachments.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF1C1C1E), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Recycle bin is empty",
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.White)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Notes or files you delete appear here and can be restored.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF8E8E93)),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Empty bin bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x33FF3B30))
                    .border(0.5f.dp, Color(0x33FF3B30))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Purge bin permanently?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Button(
                    onClick = onEmptyBinClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Empty All", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (deletedNotes.isNotEmpty()) {
                    item {
                        Text(
                            text = "DELETED NOTES (${deletedNotes.size})",
                            color = Color(0xFF8E8E93),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(deletedNotes, key = { "note_${it.id}" }) { note ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1C1C1E), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(16.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note.title.ifBlank { "Untitled Note" },
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = note.content,
                                    fontSize = 13.sp,
                                    color = Color(0xFF8E8E93),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onRestoreNote(note) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color(0xFF2C2C2E), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RestoreFromTrash,
                                        contentDescription = "Restore",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteNotePermanently(note) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color(0x24FF3B30), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteForever,
                                        contentDescription = "Perm Delete",
                                        tint = Color.Red,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (deletedAttachments.isNotEmpty()) {
                    item {
                        Text(
                            text = "DELETED ATTACHED FILES (${deletedAttachments.size})",
                            color = Color(0xFF8E8E93),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(deletedAttachments, key = { "file_${it.id}" }) { attachment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1C1C1E), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF232326), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (attachment.mimeType.startsWith("image/")) Icons.Rounded.Image else Icons.Rounded.InsertDriveFile,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = attachment.name,
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = Formatter.formatShortFileSize(
                                        LocalContext.current,
                                        attachment.size
                                    ),
                                    fontSize = 11.sp,
                                    color = Color(0xFF8E8E93)
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onRestoreAttachment(attachment) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color(0xFF2C2C2E), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Restore,
                                        contentDescription = "Restore File",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteAttachmentPermanently(attachment) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color(0x24FF3B30), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteForever,
                                        contentDescription = "Perm Delete File",
                                        tint = Color.Red,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteComposerSheet(
    viewModel: NoteViewModel,
    onDismiss: () -> Unit
) {
    val title by viewModel.editTitle.collectAsState()
    val content by viewModel.editContent.collectAsState()
    val noteColor by viewModel.editColor.collectAsState()
    val attachments by viewModel.noteAttachments.collectAsState()

    val context = LocalContext.current

    // Launcher for images
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addAttachmentToCurrentNote(it) }
    }

    // Launcher for any file format
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addAttachmentToCurrentNote(it) }
    }

    // Aesthetic card colors list
    val cardColors = listOf(
        0xFF1A1A1A.toInt(), // Custom Charcoal
        0xFF321E1E.toInt(), // Crimson
        0xFF1E3224.toInt(), // Emerald
        0xFF1E283C.toInt(), // Cobalt
        0xFF2A1E3C.toInt(), // Violet
        0xFF322C1A.toInt() // Gold
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        color = Color(0xFF0F0F12), // Deep carbon
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Elegant Header Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        "Cancel",
                        color = Color(0xFF8E8E93),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Text(
                    text = "Edit Note",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "Done",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }

            // Central scrollable editor body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Large styled Title
                BasicTextField(
                    value = title,
                    onValueChange = { viewModel.editTitle.value = it },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .testTag("editor_title_input"),
                    cursorBrush = SolidColor(Color.White),
                    decorationBox = { innerTextField ->
                        if (title.isEmpty()) {
                            Text(
                                "Title",
                                style = TextStyle(
                                    color = Color(0xFF2C2C2E),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black
                                )
                            )
                        }
                        innerTextField()
                    }
                )

                Divider(color = Color(0xFF1C1C1E), thickness = 1.dp)

                // Large Content Area
                BasicTextField(
                    value = content,
                    onValueChange = { viewModel.editContent.value = it },
                    textStyle = TextStyle(
                        color = Color(0xFFE5E5EA),
                        fontSize = 18.sp,
                        lineHeight = 26.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                        .defaultMinSize(minHeight = 150.dp)
                        .testTag("editor_content_input"),
                    cursorBrush = SolidColor(Color.White),
                    decorationBox = { innerTextField ->
                        if (content.isEmpty()) {
                            Text(
                                "Write down your amazing ideas here...",
                                style = TextStyle(
                                    color = Color(0xFF55555A),
                                    fontSize = 18.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                )

                // Dynamic Live Attachment Card Row
                if (attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "FILES & IMAGES",
                        color = Color(0xFF8E8E93),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(attachments) { attachment ->
                            AttachmentViewerCard(
                                attachment = attachment,
                                onDelete = { viewModel.removeAttachmentToRecycleBin(attachment) }
                            )
                        }
                    }
                }
            }

            // Bottom control actions panel (Aesthetic card color pickers + attachments controllers)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131316))
                    .border(0.5f.dp, Color(0xFF1C1C1E))
                    .padding(vertical = 16.dp, horizontal = 20.dp)
            ) {
                // Horizontal row indicator for Custom Color Pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Theme Color:",
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        cardColors.forEach { colorVal ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(colorVal), CircleShape)
                                    .border(
                                        width = if (noteColor == colorVal) 2.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.editColor.value = colorVal }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action panel triggers: Photos & Generic File Pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Insert Media",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Photo", color = Color.White, fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = "Insert Attachment",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Attach File", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentViewerCard(
    attachment: Attachment,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(110.dp)
            .background(Color(0xFF1C1C1E), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(16.dp))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (attachment.mimeType.startsWith("image/")) {
                // Show coil preview
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    AsyncImage(
                        model = File(attachment.uriString),
                        contentDescription = "Saved Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                // Standard Clean Document Icon
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.InsertDriveFile,
                            contentDescription = "Document file",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = attachment.name.substringAfterLast('.', "FILE").uppercase(),
                            color = Color(0xFF8E8E93),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = attachment.name,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Mini Delete badge button
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(24.dp)
                .background(Color(0xCC000000), CircleShape)
                .align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove File",
                tint = Color.Red,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
fun WidgetGuideDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "N Notes Widget Helper",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "You can place the N Notes widget onto your Android device home screen for quick note shortcuts:",
                    color = Color(0xFFE5E5EA),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "1. Long press on any empty space of your design launcher.\n" +
                            "2. Select the Widgets setting option.\n" +
                            "3. Locate and expand 'N Notes' from the provider list.\n" +
                            "4. Add the widget onto your view canvas.\n" +
                            "5. Seamlessly tap '+ Quick Note' inside the widget to compose thoughts in a micro-second!",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Understood", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF1C1C1E),
        modifier = Modifier.border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(28.dp))
    )
}
