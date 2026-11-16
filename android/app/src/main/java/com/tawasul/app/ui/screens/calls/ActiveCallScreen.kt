package com.tawasul.app.ui.screens.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tawasul.app.messaging.ActiveCallUiState
import com.tawasul.app.messaging.WebRtcCallManager
import com.tawasul.app.models.CallStatus
import com.tawasul.app.models.CallType
import com.tawasul.app.ui.components.UserAvatar
import org.webrtc.SurfaceViewRenderer

@Composable
fun ActiveCallScreen(
    peerName: String,
    peerAvatar: String,
    isVideo: Boolean,
    uiState: ActiveCallUiState,
    webRtcCallManager: WebRtcCallManager,
    onInitCall: (localRenderer: SurfaceViewRenderer?, remoteRenderer: SurfaceViewRenderer?) -> Unit,
    onToggleMute: () -> Unit,
    onSwitchCamera: () -> Unit,
    onHangUp: () -> Unit,
    onCallClosed: () -> Unit
) {
    val context = LocalContext.current
    val requiredPermissions = remember(isVideo) {
        if (isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        else arrayOf(Manifest.permission.RECORD_AUDIO)
    }
    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionsGranted = requiredPermissions.all { grants[it] == true }
        if (!permissionsGranted) onCallClosed()
    }

    val remoteRenderer = remember {
        if (isVideo) SurfaceViewRenderer(context).apply { init(webRtcCallManager.eglBase.eglBaseContext, null) }
        else null
    }
    val localRenderer = remember {
        if (isVideo) SurfaceViewRenderer(context).apply {
            init(webRtcCallManager.eglBase.eglBaseContext, null)
            setZOrderMediaOverlay(true)
        } else null
    }

    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) {
            onInitCall(localRenderer, remoteRenderer)
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(uiState.isEnded) {
        if (uiState.isEnded) onCallClosed()
    }

    DisposableEffect(Unit) {
        onDispose {
            localRenderer?.release()
            remoteRenderer?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF064E3B))
    ) {
        if (isVideo && remoteRenderer != null) {
            AndroidView(
                factory = { remoteRenderer },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isVideo && localRenderer != null) {
            AndroidView(
                factory = { localRenderer },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(width = 110.dp, height = 160.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }

        if (!isVideo || uiState.session?.type != CallType.VIDEO.name) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UserAvatar(name = peerName, avatarUrl = peerAvatar, size = 104.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = peerName,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (uiState.session?.status) {
                        CallStatus.CONNECTED.name -> "متصل عبر WebRTC"
                        CallStatus.RINGING.name -> "جاري الرنين..."
                        else -> "جاري الاتصال..."
                    },
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = if (uiState.isMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                    contentDescription = "كتم الصوت",
                    tint = Color.White
                )
            }

            FloatingActionButton(
                onClick = onHangUp,
                containerColor = Color(0xFFDC2626),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Outlined.CallEnd, contentDescription = "إنهاء المكالمة")
            }

            if (isVideo) {
                IconButton(
                    onClick = onSwitchCamera,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cameraswitch,
                        contentDescription = "تبديل الكاميرا",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
