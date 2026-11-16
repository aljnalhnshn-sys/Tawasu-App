package com.tawasul.app.ui.screens.status

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tawasul.app.models.StatusItem
import com.tawasul.app.models.StatusMediaType
import com.tawasul.app.ui.components.EmptyStateView
import com.tawasul.app.ui.components.UserAvatar
import com.tawasul.app.ui.components.formatArabicTime

@Composable
fun StatusScreen(
    statuses: List<StatusItem>,
    onPublishTextStatus: (String, String) -> Unit,
    onPublishMediaStatus: (Uri, Boolean) -> Unit,
    onViewStatus: (StatusItem) -> Unit
) {
    val context = LocalContext.current
    var showTextDialog by remember { mutableStateOf(false) }
    var activePreview by remember { mutableStateOf<StatusItem?>(null) }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            onPublishMediaStatus(uri, mime.startsWith("video/"))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { showTextDialog = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("حالة نصية")
            }
            OutlinedButton(
                onClick = {
                    mediaPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("صورة / فيديو")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (statuses.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.AutoStories,
                title = "لا توجد حالات نشطة",
                subtitle = "تختفي الحالات تلقائيًا بعد 24 ساعة من نشرها."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(statuses, key = { it.id }) { status ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onViewStatus(status)
                                activePreview = status
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                name = status.authorName,
                                avatarUrl = status.authorAvatarUrl,
                                size = 48.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = status.authorName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "@${status.authorUsername} · ${formatArabicTime(status.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = when (status.type) {
                                    StatusMediaType.IMAGE.name -> "صورة"
                                    StatusMediaType.VIDEO.name -> "فيديو"
                                    else -> "نص"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTextDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("نشر حالة جديدة (24 ساعة)") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 500) text = it },
                    placeholder = { Text("اكتب ما تود مشاركته...") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPublishTextStatus(text, "#065F46")
                        showTextDialog = false
                    },
                    enabled = text.isNotBlank()
                ) { Text("نشر") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("إلغاء") }
            }
        )
    }

    activePreview?.let { item ->
        AlertDialog(
            onDismissRequest = { activePreview = null },
            title = { Text("${item.authorName} (@${item.authorUsername})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.type == StatusMediaType.IMAGE.name && item.mediaUrl.isNotBlank()) {
                        AsyncImage(
                            model = item.mediaUrl,
                            contentDescription = "صورة الحالة",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    } else if (item.type == StatusMediaType.VIDEO.name) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("حالة فيديو جاهزة للتشغيل", fontWeight = FontWeight.Medium)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF065F46))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.textContent,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    Text(
                        text = "المشاهدات: ${item.viewerUids.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { activePreview = null }) { Text("إغلاق") }
            }
        )
    }
}
