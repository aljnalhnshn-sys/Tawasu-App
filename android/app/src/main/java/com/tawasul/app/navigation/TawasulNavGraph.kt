package com.tawasul.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tawasul.app.authentication.AuthDestination
import com.tawasul.app.authentication.AuthViewModel
import com.tawasul.app.messaging.CallViewModel
import com.tawasul.app.messaging.ChatViewModel
import com.tawasul.app.messaging.MainHubViewModel
import com.tawasul.app.models.CallType
import com.tawasul.app.ui.components.OfflineBanner
import com.tawasul.app.ui.screens.calls.ActiveCallScreen
import com.tawasul.app.ui.screens.calls.CallsScreen
import com.tawasul.app.ui.screens.chats.ChatDetailScreen
import com.tawasul.app.ui.screens.chats.ChatsListScreen
import com.tawasul.app.ui.screens.groups.GroupsScreen
import com.tawasul.app.ui.screens.onboarding.ProfileSetupScreen
import com.tawasul.app.ui.screens.profile.ProfileSettingsScreen
import com.tawasul.app.ui.screens.search.SearchAndContactsScreen
import com.tawasul.app.ui.screens.status.StatusScreen
import com.tawasul.app.ui.screens.welcome.WelcomeScreen

private data class MainTab(
    val title: String,
    val icon: ImageVector
)

private val MAIN_TABS = listOf(
    MainTab("المحادثات", Icons.Outlined.Chat),
    MainTab("البحث", Icons.Outlined.Search),
    MainTab("الحالات", Icons.Outlined.AutoStories),
    MainTab("المجموعات", Icons.Outlined.Groups),
    MainTab("المكالمات", Icons.Outlined.Call),
    MainTab("حسابي", Icons.Outlined.Person)
)

@Composable
fun TawasulAppRoot(
    authViewModel: AuthViewModel = hiltViewModel(),
    isDarkTheme: Boolean,
    onThemeChanged: (Boolean?) -> Unit
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    when (authState.destination) {
        AuthDestination.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AuthDestination.Welcome -> {
            WelcomeScreen(
                isBusy = authState.isBusy,
                errorMessage = authState.errorMessage,
                onGoogleSignInClick = { ctx -> authViewModel.signInWithGoogle(ctx) },
                onDismissError = authViewModel::clearError
            )
        }
        AuthDestination.ProfileSetup -> {
            ProfileSetupScreen(
                suggestedDisplayName = authState.suggestedDisplayName,
                suggestedAvatarUrl = authState.suggestedAvatarUrl,
                isBusy = authState.isBusy,
                isUsernameAvailable = authState.isUsernameAvailable,
                usernameHint = authState.usernameAvailabilityHint,
                errorMessage = authState.errorMessage,
                onUsernameChanged = authViewModel::checkUsernameAvailability,
                onSubmitProfile = authViewModel::completeProfileSetup
            )
        }
        AuthDestination.MainApp -> {
            MainAppNavHost(
                currentUid = authState.currentProfile?.uid.orEmpty(),
                isDarkTheme = isDarkTheme,
                onThemeChanged = onThemeChanged,
                onSignOut = authViewModel::signOut,
                onDeleteAccount = { onErr -> authViewModel.deleteAccount(onErr) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppNavHost(
    currentUid: String,
    isDarkTheme: Boolean,
    onThemeChanged: (Boolean?) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: ((String) -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val hubViewModel: MainHubViewModel = hiltViewModel()
    val hubState by hubViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(hubState.actionMessage) {
        val msg = hubState.actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            hubViewModel.clearActionMessage()
        }
    }

    hubState.incomingCall?.let { incoming ->
        AlertDialog(
            onDismissRequest = { hubViewModel.declineIncomingCall(incoming.id) },
            title = { Text("مكالمة واردة") },
            text = { Text("${incoming.callerName} يتصل بك الآن (${if (incoming.type == CallType.VIDEO.name) "مرئية" else "صوتية"})") },
            confirmButton = {
                TextButton(
                    onClick = {
                        navController.navigate(
                            "call?callId=${incoming.id}&peerUid=${incoming.callerUid}&peerName=${Uri.encode(incoming.callerName)}&isVideo=${incoming.type == CallType.VIDEO.name}&incoming=true"
                        )
                    }
                ) { Text("رد") }
            },
            dismissButton = {
                TextButton(onClick = { hubViewModel.declineIncomingCall(incoming.id) }) {
                    Text("رفض")
                }
            }
        )
    }

    NavHost(navController = navController, startDestination = "hub") {
        composable("hub") {
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }
            val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "تواصل · ${MAIN_TABS[selectedTab].title}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                },
                bottomBar = {
                    if (!isWideScreen) {
                        NavigationBar {
                            MAIN_TABS.forEachIndexed { idx, tab ->
                                NavigationBarItem(
                                    selected = selectedTab == idx,
                                    onClick = { selectedTab = idx },
                                    icon = { Icon(tab.icon, contentDescription = tab.title) },
                                    label = { Text(tab.title, maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (isWideScreen) {
                        NavigationRail {
                            MAIN_TABS.forEachIndexed { idx, tab ->
                                NavigationRailItem(
                                    selected = selectedTab == idx,
                                    onClick = { selectedTab = idx },
                                    icon = { Icon(tab.icon, contentDescription = tab.title) },
                                    label = { Text(tab.title) }
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        OfflineBanner(isOnline = hubState.isOnline)
                        when (selectedTab) {
                            0 -> ChatsListScreen(
                                currentUid = currentUid,
                                chats = hubState.chats,
                                peerProfiles = hubState.peerProfiles,
                                isLoading = hubState.isLoadingChats,
                                onOpenChat = { chat ->
                                    val peerUid = chat.participantIds.firstOrNull { it != currentUid }.orEmpty()
                                    val participants = Uri.encode(chat.participantIds.joinToString(","))
                                    val title = Uri.encode(chat.title)
                                    navController.navigate(
                                        "chat/${chat.id}?isGroup=${chat.isGroup}&title=$title&peerUid=$peerUid&participants=$participants"
                                    )
                                }
                            )
                            1 -> SearchAndContactsScreen(
                                query = hubState.searchQuery,
                                isSearching = hubState.isSearching,
                                results = hubState.searchResults,
                                onQueryChange = hubViewModel::onSearchQueryChanged,
                                onMessageUser = { target ->
                                    hubViewModel.startOrOpenDirectChat(target) { chat ->
                                        val participants = Uri.encode(chat.participantIds.joinToString(","))
                                        navController.navigate(
                                            "chat/${chat.id}?isGroup=false&title=&peerUid=${target.uid}&participants=$participants"
                                        )
                                    }
                                },
                                onBlockUser = hubViewModel::blockUser
                            )
                            2 -> StatusScreen(
                                statuses = hubState.statuses,
                                onPublishTextStatus = hubViewModel::publishTextStatus,
                                onPublishMediaStatus = hubViewModel::publishMediaStatus,
                                onViewStatus = { item -> hubViewModel.markStatusViewed(item.id) }
                            )
                            3 -> GroupsScreen(
                                currentUid = currentUid,
                                groupChats = hubState.chats.filter { it.isGroup },
                                knownUsers = hubState.peerProfiles.values.toList(),
                                onSearchUsers = hubViewModel::onSearchQueryChanged,
                                searchResults = hubState.searchResults,
                                onCreateGroup = { title, members ->
                                    hubViewModel.createGroup(title, members) { chat ->
                                        val participants = Uri.encode(chat.participantIds.joinToString(","))
                                        navController.navigate(
                                            "chat/${chat.id}?isGroup=true&title=${Uri.encode(chat.title)}&peerUid=&participants=$participants"
                                        )
                                    }
                                },
                                onOpenGroup = { group ->
                                    val participants = Uri.encode(group.participantIds.joinToString(","))
                                    navController.navigate(
                                        "chat/${group.id}?isGroup=true&title=${Uri.encode(group.title)}&peerUid=&participants=$participants"
                                    )
                                }
                            )
                            4 -> CallsScreen(
                                currentUid = currentUid,
                                callHistory = hubState.callHistory
                            )
                            5 -> ProfileSettingsScreen(
                                profile = hubState.currentUser,
                                blockedUsers = hubState.blockedUsers,
                                isDarkMode = isDarkTheme,
                                onSaveProfile = hubViewModel::updateProfileDetails,
                                onUpdatePrivacy = hubViewModel::updatePrivacy,
                                onUnblockUser = hubViewModel::unblockUser,
                                onThemeChanged = onThemeChanged,
                                onSignOut = onSignOut,
                                onDeleteAccount = {
                                    onDeleteAccount { err ->
                                        hubViewModel.showMessage(err)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        composable(
            route = "chat/{chatId}?isGroup={isGroup}&title={title}&peerUid={peerUid}&participants={participants}",
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("isGroup") { type = NavType.BoolType; defaultValue = false },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("peerUid") { type = NavType.StringType; defaultValue = "" },
                navArgument("participants") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
            val isGroup = backStackEntry.arguments?.getBoolean("isGroup") ?: false
            val title = backStackEntry.arguments?.getString("title").orEmpty()
            val peerUid = backStackEntry.arguments?.getString("peerUid").orEmpty()
            val participants = backStackEntry.arguments?.getString("participants")
                .orEmpty().split(",").filter { it.isNotBlank() }

            val chatViewModel: ChatViewModel = hiltViewModel()
            val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(chatId) {
                chatViewModel.openChat(chatId, participants, isGroup, title, peerUid)
            }

            ChatDetailScreen(
                currentUid = currentUid,
                uiState = chatState,
                isDarkTheme = isDarkTheme,
                onBack = { navController.popBackStack() },
                onLoadOlder = chatViewModel::loadOlderMessages,
                onSendText = chatViewModel::sendTextMessage,
                onRetryFailed = chatViewModel::retryFailedMessage,
                onSendMedia = chatViewModel::sendMediaUri,
                onStartVoice = chatViewModel::startVoiceRecording,
                onStopAndSendVoice = chatViewModel::stopAndSendVoiceRecording,
                onDeleteMessageForMe = chatViewModel::deleteMessageForMe,
                onClearError = chatViewModel::clearError,
                onStartAudioCall = {
                    val peer = chatState.peerProfile ?: return@ChatDetailScreen
                    navController.navigate(
                        "call?callId=&peerUid=${peer.uid}&peerName=${Uri.encode(peer.displayName)}&isVideo=false&incoming=false"
                    )
                },
                onStartVideoCall = {
                    val peer = chatState.peerProfile ?: return@ChatDetailScreen
                    navController.navigate(
                        "call?callId=&peerUid=${peer.uid}&peerName=${Uri.encode(peer.displayName)}&isVideo=true&incoming=false"
                    )
                }
            )
        }

        composable(
            route = "call?callId={callId}&peerUid={peerUid}&peerName={peerName}&isVideo={isVideo}&incoming={incoming}",
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType; defaultValue = "" },
                navArgument("peerUid") { type = NavType.StringType; defaultValue = "" },
                navArgument("peerName") { type = NavType.StringType; defaultValue = "" },
                navArgument("isVideo") { type = NavType.BoolType; defaultValue = false },
                navArgument("incoming") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId").orEmpty()
            val peerUid = backStackEntry.arguments?.getString("peerUid").orEmpty()
            val peerName = backStackEntry.arguments?.getString("peerName").orEmpty()
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
            val incoming = backStackEntry.arguments?.getBoolean("incoming") ?: false

            val callViewModel: CallViewModel = hiltViewModel()
            val callState by callViewModel.uiState.collectAsStateWithLifecycle()

            ActiveCallScreen(
                peerName = peerName,
                peerAvatar = hubState.peerProfiles[peerUid]?.avatarUrl.orEmpty(),
                isVideo = isVideo,
                uiState = callState,
                webRtcCallManager = callViewModel.webRtcCallManager,
                onInitCall = { localRenderer, remoteRenderer ->
                    if (incoming && callId.isNotBlank()) {
                        callViewModel.acceptIncomingCall(callId, localRenderer, remoteRenderer)
                    } else {
                        val me = hubState.currentUser ?: return@ActiveCallScreen
                        val peer = hubState.peerProfiles[peerUid] ?: return@ActiveCallScreen
                        callViewModel.startOutgoingCall(
                            caller = me,
                            callee = peer,
                            type = if (isVideo) CallType.VIDEO else CallType.AUDIO,
                            localRenderer = localRenderer,
                            remoteRenderer = remoteRenderer
                        )
                    }
                },
                onToggleMute = callViewModel::toggleMute,
                onSwitchCamera = callViewModel::switchCamera,
                onHangUp = callViewModel::hangUp,
                onCallClosed = { navController.popBackStack() }
            )
        }
    }
}
