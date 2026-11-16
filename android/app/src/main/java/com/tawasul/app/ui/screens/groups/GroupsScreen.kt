package com.tawasul.app.ui.screens.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tawasul.app.models.Chat
import com.tawasul.app.models.UserProfile
import com.tawasul.app.ui.components.EmptyStateView
import com.tawasul.app.ui.components.UserAvatar
import com.tawasul.app.ui.components.formatArabicTime

@Composable
fun GroupsScreen(
    currentUid: String,
    groupChats: List<Chat>,
    knownUsers: List<UserProfile>,
    onSearchUsers: (String) -> Unit,
    searchResults: List<UserProfile>,
    onCreateGroup: (title: String, memberUids: List<String>) -> Unit,
    onOpenGroup: (Chat) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = { showCreateDialog = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.GroupAdd, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("إنشاء مجموعة جديدة")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (groupChats.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.Groups,
                title = "لا توجد مجموعات",
                subtitle = "أنشئ مجموعة وأضف أعضاء عبر أسماء المستخدمين الخاصة بهم."
            )
        } else {
            LazyColumn {
                items(groupChats, key = { it.id }) { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenGroup(group) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            name = group.title,
                            avatarUrl = group.avatarUrl,
                            size = 50.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${group.participantIds.size} أعضاء · ${group.lastMessageText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatArabicTime(group.lastMessageAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (showCreateDialog) {
        var groupTitle by remember { mutableStateOf("") }
        var memberQuery by remember { mutableStateOf("") }
        val selectedUids = remember { mutableStateListOf<String>() }
        val candidates = (knownUsers + searchResults).distinctBy { it.uid }.filter { it.uid != currentUid }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("إنشاء مجموعة جديدة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = groupTitle,
                        onValueChange = { groupTitle = it },
                        label = { Text("اسم المجموعة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = memberQuery,
                        onValueChange = {
                            memberQuery = it
                            onSearchUsers(it)
                        },
                        label = { Text("ابحث لإضافة أعضاء (@username)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(modifier = Modifier.height(160.dp)) {
                        items(candidates, key = { it.uid }) { candidate ->
                            val checked = candidate.uid in selectedUids
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (checked) selectedUids.remove(candidate.uid)
                                        else selectedUids.add(candidate.uid)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) selectedUids.add(candidate.uid)
                                        else selectedUids.remove(candidate.uid)
                                    }
                                )
                                Text("${candidate.displayName} (@${candidate.username})")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateGroup(groupTitle, selectedUids.toList())
                        showCreateDialog = false
                    },
                    enabled = groupTitle.trim().length >= 2 && selectedUids.isNotEmpty()
                ) { Text("إنشاء") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
