package com.tawasul.app.ui.screens.calls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tawasul.app.models.CallSession
import com.tawasul.app.models.CallType
import com.tawasul.app.ui.components.EmptyStateView
import com.tawasul.app.ui.components.UserAvatar
import com.tawasul.app.ui.components.formatArabicTime

@Composable
fun CallsScreen(
    currentUid: String,
    callHistory: List<CallSession>
) {
    if (callHistory.isEmpty()) {
        EmptyStateView(
            icon = Icons.Outlined.Call,
            title = "سجل المكالمات فارغ",
            subtitle = "يمكنك بدء مكالمة صوتية أو مرئية عبر WebRTC مباشرة من داخل أي محادثة ثنائية."
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(callHistory, key = { it.id }) { call ->
            val isOutgoing = call.callerUid == currentUid
            val peerName = if (isOutgoing) call.calleeName else call.callerName
            val peerAvatar = if (isOutgoing) call.calleeAvatarUrl else call.callerAvatarUrl

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    name = peerName,
                    avatarUrl = peerAvatar,
                    size = 48.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isOutgoing) Icons.Outlined.CallMade else Icons.Outlined.CallReceived,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${if (isOutgoing) "صادرة" else "واردة"} · ${formatArabicTime(call.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (call.type == CallType.VIDEO.name) Icons.Outlined.Videocam else Icons.Outlined.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
