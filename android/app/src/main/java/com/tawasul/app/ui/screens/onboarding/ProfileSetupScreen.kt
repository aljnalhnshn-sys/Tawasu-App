package com.tawasul.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tawasul.app.ui.components.UserAvatar

@Composable
fun ProfileSetupScreen(
    suggestedDisplayName: String,
    suggestedAvatarUrl: String,
    isBusy: Boolean,
    isUsernameAvailable: Boolean?,
    usernameHint: String?,
    errorMessage: String?,
    onUsernameChanged: (String) -> Unit,
    onSubmitProfile: (displayName: String, username: String, bio: String) -> Unit
) {
    var displayName by remember(suggestedDisplayName) { mutableStateOf(suggestedDisplayName) }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            UserAvatar(
                name = displayName.ifBlank { "ت" },
                avatarUrl = suggestedAvatarUrl,
                size = 84.dp
            )

            Text(
                text = "إعداد ملفك الشخصي",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "اختر اسمًا ظاهرًا واسم مستخدم فريدًا ليعثر عليك الآخرون بسهولة دون رقم هاتف.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("الاسم الظاهر") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = username,
                onValueChange = { input ->
                    username = input
                    onUsernameChanged(input)
                },
                label = { Text("اسم المستخدم الفريد (3-20 حرفًا إنجليزيًا)") },
                placeholder = { Text("مثال: ahmad_99") },
                prefix = { Text("@") },
                singleLine = true,
                isError = isUsernameAvailable == false,
                supportingText = {
                    if (!usernameHint.isNullOrBlank()) {
                        Text(
                            text = usernameHint,
                            color = if (isUsernameAvailable == true) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    } else {
                        Text("أحرف إنجليزية وأرقام وشرطة سفلية (_) فقط، وغير حساس لحالة الأحرف.")
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 160) bio = it },
                label = { Text("نبذة قصيرة (اختياري)") },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onSubmitProfile(displayName, username, bio) },
                enabled = !isBusy && displayName.trim().length >= 2 && isUsernameAvailable == true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("إنشاء الحساب والبدء", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
