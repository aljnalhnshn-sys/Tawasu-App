package com.tawasul.app.ui.screens.chats

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.tawasul.app.models.DeliveryStatus
import com.tawasul.app.models.Message
import com.tawasul.app.models.MessageType
import com.tawasul.app.messaging.ChatDetailUiState
import com.tawasul.app.ui.components.OfflineBanner
import com.tawasul.app.ui.components.UserAvatar
import com.tawasul.app.ui.components.formatArabicTime
import com.tawasul.app.ui.theme.ReceivedBubbleDark
import com.tawasul.app.ui.theme.ReceivedBubbleLight
import com.tawasul.app.ui.theme.SentBubbleDark
import com.tawasul.app.ui.theme.SentBubbleLight

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    currentUid: String,
    uiState: ChatDetailUiState,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onLoadOlder: () -> Unit,
    onSendText: (String) -> Unit,
    onRetryFailed: (Message) -> Unit,
    onSendMedia: (Uri, Boolean) -> Unit,
    onStartVoice: () -> Unit,
    onStopAndSendVoice: () -> Unit,
    onDeleteMessageForMe: (String) -> Unit,
    onClearError: () -> Unit,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit
) {
    val context = LocalContext.current
    var draftText by remember { mutableStateOf("") }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }
    var playingAudioId by remember { mutableStateOf<String?>(null) }
    var activeVideoUrl by remember { mutableStateOf<String?>(null) }

    val audioPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onDispose { audioPlayer.release() }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            onSendMedia(uri, mime.startsWith("video/"))
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartVoice()
    }

    val combinedMessages = remember(uiState.messages, uiState.pendingOrFailedMessages) {
        uiState.messages + uiState.pendingOrFailedMessages
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            name = uiState.chatTitle.ifBlank { "ت" },
                            avatarUrl = uiState.peerProfile?.avatarUrl.orEmpty(),
                            size = 38.dp,
                            showOnlineIndicator = !uiState.isGroup && uiState.peerProfile?.showOnlineStatus == true,
                            isOnline = uiState.peerProfile?.isOnline == true
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = uiState.chatTitle.ifBlank { "محادثة" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val subtitle = when {
                                uiState.isGroup -> "${uiState.participantIds.size} أعضاء"
                                uiState.peerProfile?.showOnlineStatus == true && uiState.peerProfile.isOnline -> "متصل الآن"
                                uiState.peerProfile != null -> "@${uiState.peerProfile.username}"
                                else -> ""
                            }
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (!uiState.isGroup && uiState.peerProfile != null) {
                        IconButton(onClick = onStartAudioCall) {
                            Icon(Icons.Outlined.Call, contentDescription = "مكالمة صوتية")
                        }
                        IconButton(onClick = onStartVideoCall) {
                            Icon(Icons.Outlined.Videocam, contentDescription = "مكالمة مرئية")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OfflineBanner(isOnline = uiState.isOnline)
            if (!uiState.errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    onClick = onClearError,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            if (uiState.isUploadingMedia) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.hasMoreOlder && uiState.messages.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = onLoadOlder,
                                enabled = !uiState.isLoadingOlder
                            ) {
                                Text(
                                    if (uiState.isLoadingOlder) "جاري التحميل..." else "تحميل رسائل أقدم"
                                )
                            }
                        }
                    }
                }

                items(combinedMessages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == currentUid
                    val bubbleBg = when {
                        isMine && isDarkTheme -> SentBubbleDark
                        isMine -> SentBubbleLight
                        isDarkTheme -> ReceivedBubbleDark
                        else -> ReceivedBubbleLight
                    }
                    val textColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                    ) {
                        Surface(
                            color = bubbleBg,
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isMine) 18.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 18.dp
                            ),
                            tonalElevation = if (isMine) 0.dp else 1.dp,
                            modifier = Modifier
                                .widthIn(max = 290.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (msg.statusEnum == DeliveryStatus.FAILED) {
                                            onRetryFailed(msg)
                                        }
                                    },
                                    onLongClick = {
                                        if (!msg.id.startsWith("local_")) {
                                            messageToDelete = msg
                                        }
                                    }
                                )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                when (msg.messageTypeEnum) {
                                    MessageType.IMAGE -> {
                                        AsyncImage(
                                            model = msg.mediaUrl,
                                            contentDescription = "صورة مرسلة",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(190.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    }
                                    MessageType.VIDEO -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.clickable {
                                                if (msg.mediaUrl.isNotBlank()) activeVideoUrl = msg.mediaUrl
                                            }
                                        ) {
                                            Icon(
                                                Icons.Rounded.PlayArrow,
                                                contentDescription = "تشغيل الفيديو",
                                                tint = textColor
                                            )
                                            Text("تشغيل مقطع الفيديو", color = textColor)
                                        }
                                    }
                                    MessageType.AUDIO -> {
                                        val isPlayingThis = playingAudioId == msg.id
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.clickable {
                                                if (isPlayingThis) {
                                                    audioPlayer.pause()
                                                    playingAudioId = null
                                                } else if (msg.mediaUrl.isNotBlank()) {
                                                    audioPlayer.setMediaItem(MediaItem.fromUri(msg.mediaUrl))
                                                    audioPlayer.prepare()
                                                    audioPlayer.play()
                                                    playingAudioId = msg.id
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                contentDescription = "تشغيل الصوت",
                                                tint = textColor
                                            )
                                            Text(
                                                text = "رسالة صوتية (${msg.mediaDurationSec} ث)",
                                                color = textColor
                                            )
                                        }
                                    }
                                    else -> {
                                        Text(
                                            text = msg.text,
                                            color = textColor,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = formatArabicTime(msg.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textColor.copy(alpha = 0.72f)
                                    )
                                    if (isMine) {
                                        when (msg.statusEnum) {
                                            DeliveryStatus.READ -> Icon(
                                                Icons.Rounded.DoneAll,
                                                contentDescription = "مقروءة",
                                                tint = Color(0xFF6EE7B7),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            DeliveryStatus.DELIVERED -> Icon(
                                                Icons.Rounded.DoneAll,
                                                contentDescription = "تم الوصول",
                                                tint = textColor.copy(alpha = 0.75f),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            DeliveryStatus.SENT -> Icon(
                                                Icons.Rounded.Done,
                                                contentDescription = "تم الإرسال",
                                                tint = textColor.copy(alpha = 0.75f),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            DeliveryStatus.FAILED -> Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Refresh,
                                                    contentDescription = "إعادة المحاولة",
                                                    tint = Color(0xFFFCA5A5),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = "فشل الإرسال — اضغط للإعادة",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFFCA5A5)
                                                )
                                            }
                                            DeliveryStatus.SENDING -> Text(
                                                text = "جاري الإرسال...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = textColor.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        mediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = "إرسال صورة أو فيديو")
                }

                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    placeholder = { Text("اكتب رسالة...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f)
                )

                if (draftText.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onSendText(draftText)
                            draftText = ""
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "إرسال",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (uiState.isRecordingVoice) {
                                onStopAndSendVoice()
                            } else {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) onStartVoice()
                                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (uiState.isRecordingVoice) Icons.Outlined.Stop else Icons.Outlined.Mic,
                            contentDescription = if (uiState.isRecordingVoice) "إيقاف وإرسال الصوت" else "تسجيل رسالة صوتية",
                            tint = if (uiState.isRecordingVoice) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    messageToDelete?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("حذف الرسالة من واجهتك") },
            text = {
                Text(
                    "سيؤدي هذا الإجراء إلى إخفاء الرسالة من حسابك وجهازك فقط، ولن يتم حذفها من أجهزة المشاركين الآخرين."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMessageForMe(msg.id)
                        messageToDelete = null
                    }
                ) {
                    Text("حذف لديّ فقط", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    activeVideoUrl?.let { videoUrl ->
        val videoPlayer = remember(videoUrl) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                playWhenReady = true
            }
        }
        DisposableEffect(videoUrl) {
            onDispose { videoPlayer.release() }
        }
        AlertDialog(
            onDismissRequest = { activeVideoUrl = null },
            title = { Text("مشاهدة الفيديو") },
            text = {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply { player = videoPlayer }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { activeVideoUrl = null }) { Text("إغلاق") }
            }
        )
    }
}
