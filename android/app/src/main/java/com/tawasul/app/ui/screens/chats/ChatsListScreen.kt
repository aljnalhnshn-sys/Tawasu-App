package com.tawasul.app.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tawasul.app.models.Chat
import com.tawasul.app.models.UserProfile
import com.tawasul.app.ui.components.EmptyStateView
import com.tawasul.app.ui.components.UserAvatar
import com.tawasul.app.ui.components.formatArabicTime

@Composable
fun ChatsListScreen(
    currentUid: String,
    chats: List<Chat>,
    peerProfiles: Map<String, UserProfile>,
    isLoading: Boolean,
    onOpenChat: (Chat) -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (chats.isEmpty()) {
        EmptyStateView(
            icon = Icons.Outlined.ChatBubbleOutline,
            title = "لا توجد محادثات بعد",
            subtitle = "انتقل إلى تبويب البحث للعثور على أصدقائك عبر اسم المستخدم وبدء المحادثة."
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(chats, key = { it.id }) { chat ->
            val peerUid = chat.participantIds.firstOrNull { it != currentUid }
            val peer = if (!chat.isGroup && peerUid != null) peerProfiles[peerUid] else null
            val displayName = if (chat.isGroup) {
                chat.title.ifBlank { "مجموعة" }
            } else {
                peer?.displayName ?: "مستخدم"
            }
            val subhandle = if (chat.isGroup) {
                "${chat.participantIds.size} أعضاء"
            } else {
                peer?.username?.let { "@$it" }.orEmpty()
            }
            val unreadCount = chat.unreadCounts[currentUid] ?: 0
            val showPresence = peer != null && peer.showOnlineStatus

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenChat(chat) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    name = displayName,
                    avatarUrl = if (chat.isGroup) chat.avatarUrl else peer?.avatarUrl.orEmpty(),
                    size = 52.dp,
                    showOnlineIndicator = showPresence,
                    isOnline = peer?.isOnline == true
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatArabicTime(chat.lastMessageAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unreadCount > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = chat.lastMessageText.ifBlank { subhandle },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = unreadCount.coerceAtMost(99).toString(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 80.dp)
            )
        }
    }
}
