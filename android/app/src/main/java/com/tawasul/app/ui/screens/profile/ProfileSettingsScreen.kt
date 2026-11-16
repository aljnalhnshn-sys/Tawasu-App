package com.tawasul.app.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tawasul.app.models.BlockedUser
import com.tawasul.app.models.GroupPrivacySetting
import com.tawasul.app.models.UserProfile
import com.tawasul.app.ui.components.UserAvatar

@Composable
fun ProfileSettingsScreen(
    profile: UserProfile?,
    blockedUsers: List<BlockedUser>,
    isDarkMode: Boolean?,
    onSaveProfile: (displayName: String, bio: String, newUsername: String?, avatarUri: Uri?) -> Unit,
    onUpdatePrivacy: (showOnline: Boolean, showReceipts: Boolean, groupSetting: GroupPrivacySetting) -> Unit,
    onUnblockUser: (String) -> Unit,
    onThemeChanged: (Boolean?) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    if (profile == null) return

    var displayName by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var bio by remember(profile.bio) { mutableStateOf(profile.bio) }
    var username by remember(profile.username) { mutableStateOf(profile.username) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }

    var showOnline by remember(profile.showOnlineStatus) { mutableStateOf(profile.showOnlineStatus) }
    var showReceipts by remember(profile.showReadReceipts) { mutableStateOf(profile.showReadReceipts) }
    var confirmDelete by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) selectedAvatarUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            UserAvatar(
                name = profile.displayName,
                avatarUrl = selectedAvatarUri?.toString() ?: profile.avatarUrl,
                size = 68.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("@${profile.username}", color = MaterialTheme.colorScheme.primary)
            }
            OutlinedButton(
                onClick = {
                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("تغيير الصورة")
            }
        }

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("الاسم الظاهر") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("اسم المستخدم الفريد") },
            prefix = { Text("@") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = bio,
            onValueChange = { if (it.length <= 160) bio = it },
            label = { Text("النبذة الشخصية") },
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val changedUsername = if (username.trim().lowercase() != profile.usernameLower) username else null
                onSaveProfile(displayName, bio, changedUsername, selectedAvatarUri)
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("حفظ تعديلات الملف الشخصي")
        }

        HorizontalDivider()
        Text("الخصوصية والمظهر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        val groupSetting = GroupPrivacySetting.entries.firstOrNull {
            it.name == profile.whoCanAddToGroups
        } ?: GroupPrivacySetting.EVERYONE

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("إظهار حالة الاتصال (متصل الآن)")
            Switch(
                checked = showOnline,
                onCheckedChange = {
                    showOnline = it
                    onUpdatePrivacy(showOnline, showReceipts, groupSetting)
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("إرسال إشعارات القراءة")
            Switch(
                checked = showReceipts,
                onCheckedChange = {
                    showReceipts = it
                    onUpdatePrivacy(showOnline, showReceipts, groupSetting)
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("منع إضافتي للمجموعات من غير الجهات")
            Switch(
                checked = groupSetting == GroupPrivacySetting.CONTACTS_ONLY,
                onCheckedChange = { onlyContacts ->
                    onUpdatePrivacy(
                        showOnline,
                        showReceipts,
                        if (onlyContacts) GroupPrivacySetting.CONTACTS_ONLY else GroupPrivacySetting.EVERYONE
                    )
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الوضع الداكن")
            Switch(
                checked = isDarkMode == true,
                onCheckedChange = { onThemeChanged(it) }
            )
        }

        if (blockedUsers.isNotEmpty()) {
            HorizontalDivider()
            Text("المستخدمون المحظورون (${blockedUsers.size})", fontWeight = FontWeight.Bold)
            blockedUsers.forEach { blocked ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${blocked.blockedDisplayName} (@${blocked.blockedUsername})")
                    TextButton(onClick = { onUnblockUser(blocked.blockedUid) }) {
                        Text("إلغاء الحظر")
                    }
                }
            }
        }

        HorizontalDivider()
        OutlinedButton(
            onClick = onSignOut,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("تسجيل الخروج")
        }

        Button(
            onClick = { confirmDelete = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("حذف الحساب نهائيًا")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("تأكيد حذف الحساب") },
            text = {
                Text(
                    "سيتم حذف ملفك الشخصي وبياناتك الخاصة وقائمة الحظر وتحرير اسم المستخدم @${profile.username} نهائيًا، ولن يمكن التراجع عن ذلك."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteAccount()
                    }
                ) {
                    Text("حذف نهائي", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") }
            }
        )
    }
}
