package com.jhani.measurear.capture

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jhani.measurear.R
import com.jhani.measurear.measurement.MeasureUnit
import com.jhani.measurear.measurement.formatArea
import com.jhani.measurear.measurement.formatValue
import com.jhani.measurear.measurement.ResultValue
import com.jhani.measurear.measurement.ValueKind
import com.jhani.measurear.measurement.formatDistance
import com.jhani.measurear.measurement.formatLength
import com.jhani.measurear.presentation.HudTeal
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private val HistoryBackground = Color(0xFF0E1414)
private val CardColor = Color(0xFF172121)
private val DangerColor = Color(0xFFFF6B6B)

/**
 * Saved measurements, newest first. Each card shows the photo, name, time and values, with
 * rename, share and delete; the header has Clear all.
 */
@Composable
fun HistoryScreen(unit: MeasureUnit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val records by HistoryStore.records.collectAsState()

    var renaming by remember { mutableStateOf<HistoryRecord?>(null) }
    val currentProject by HistoryStore.currentProject.collectAsState()
    val projects by HistoryStore.projects.collectAsState()
    var newProject by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<HistoryRecord?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { HistoryStore.load(context) }
    BackHandler(onBack = onClose)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HistoryBackground)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIcon(R.drawable.ic_close, "Close", Color.White, onClose)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Saved measurements",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            if (!records.isNullOrEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("Clear all", color = DangerColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Projects: the selected one filters the list and receives new measurements
        ProjectChips(
            projects = projects,
            selected = currentProject,
            onSelect = { HistoryStore.setCurrentProject(context, it) },
            onNew = { newProject = true },
            onExport = currentProject?.let { project ->
                {
                    scope.launch {
                        val inProject = records.orEmpty().filter { it.project == project }
                        val pdf = HistoryStore.exportPdf(context, project, inProject, unit)
                        HistoryStore.sharePdf(context, pdf)
                    }
                    Unit
                }
            }
        )

        val list = records?.let { all -> currentProject?.let { p -> all.filter { it.project == p } } ?: all }
        when {
            list == null -> Unit
            list.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No saved measurements yet.\nEvery line and area you measure is saved here automatically.",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(32.dp)
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                items(list, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        unit = unit,
                        onRename = { renaming = record },
                        onMove = { moving = record },
                        onShare = { HistoryStore.share(context, record, unit) },
                        onDelete = { scope.launch { HistoryStore.delete(context, record.id) } }
                    )
                }
            }
        }
    }

    if (newProject) {
        RenameDialog(
            initial = "",
            title = "New project",
            placeholder = "e.g. Living room",
            onConfirm = { name ->
                HistoryStore.setCurrentProject(context, name)
                newProject = false
            },
            onDismiss = { newProject = false }
        )
    }

    moving?.let { record ->
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text("Move to project") },
            text = {
                Column {
                    (listOf<String?>(null) + projects).forEach { p ->
                        Text(
                            p ?: "No project",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (p == record.project) HudTeal.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    scope.launch { HistoryStore.move(context, record.id, p) }
                                    moving = null
                                }
                                .padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } }
        )
    }

    renaming?.let { record ->
        RenameDialog(
            initial = record.name,
            onConfirm = { name ->
                scope.launch { HistoryStore.rename(context, record.id, name) }
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all saved measurements?") },
            text = { Text("This deletes every saved measurement and photo. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { HistoryStore.clearAll(context) }
                    confirmClear = false
                }) { Text("Clear all", color = DangerColor) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RecordCard(
    record: HistoryRecord,
    unit: MeasureUnit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val thumbnail by produceState<Bitmap?>(initialValue = null, record.id) {
        value = HistoryStore.thumbnail(record, targetWidth = 360)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(84.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
        ) {
            thumbnail?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // ✕ delete this record
                RoundIcon(R.drawable.ic_close, "Delete", Color.White.copy(alpha = 0.7f), onDelete, size = 32)
            }
            Text(
                text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = HudTeal
            )
            Spacer(modifier = Modifier.height(6.dp))
            record.items.forEach { item ->
                Text(
                    text = if (item.label != null || item.kind != ValueKind.LENGTH) {
                        (item.label ?: "Area") + "  " + formatValue(ResultValue(item.label ?: "", item.value, item.kind), item.isEstimate, unit)
                    } else {
                        formatDistance(item.value, item.isEstimate, unit) +
                            "   ↔ ${formatLength(item.horizontal, unit)}  ↕ ${formatLength(item.vertical, unit)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(R.drawable.ic_edit, "Rename", Color.White, onRename)
                SmallAction(R.drawable.ic_share, "Share", HudTeal, onShare)
                SmallAction(R.drawable.ic_history, record.project ?: "Project", Color.White.copy(alpha = 0.8f), onMove)
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Name this measurement",
    placeholder: String = "e.g. Kitchen table width"
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(placeholder) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HudTeal, cursorColor = HudTeal)
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save", color = HudTeal) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RoundIcon(icon: Int, description: String, tint: Color, onClick: () -> Unit, size: Int = 44) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size((size / 2).dp))
    }
}

@Composable
private fun SmallAction(icon: Int, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

/** "All", each project and "+ New"; with a project selected, a PDF export button. */
@Composable
private fun ProjectChips(
    projects: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onNew: () -> Unit,
    onExport: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (listOf<String?>(null) + projects).forEach { p ->
            val on = p == selected
            Text(
                p ?: "All",
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) HudTeal else CardColor)
                    .clickable { onSelect(p) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                color = if (on) Color.Black else Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Text(
            "+ New",
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, HudTeal.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .clickable(onClick = onNew)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            color = HudTeal,
            style = MaterialTheme.typography.labelLarge
        )
        if (onExport != null) {
            Text(
                "PDF report",
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onExport)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
    if (selected != null) {
        Text(
            "New measurements are saved to \"$selected\"",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}
