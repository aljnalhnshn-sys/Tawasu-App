package com.tawasul.app.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tawasul.app.models.UserProfile
import com.tawasul.app.ui.components.EmptyStateView
import com.tawasul.app.ui.components.UserAvatar

@Composable
fun SearchAndContactsScreen(
    query: String,
    isSearching: Boolean,
    results: List<UserProfile>,
    onQueryChange: (String) -> Unit,
    onMessageUser: (UserProfile) -> Unit,
    onBlockUser: (UserProfile) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("ابحث عن اسم المستخدم") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (query.isBlank()) {
            EmptyStateView(
                icon = Icons.Outlined.PersonSearch,
                title = "ابحث باسم المستخدم",
                subtitle = "أدخل اسم المستخدم الكامل أو بدايته للعثور على الأشخاص ومراسلتهم دون الحاجة لرقم الهاتف."
            )
        } else if (results.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.PersonSearch,
                title = "لا توجد نتائج مطابقة",
                subtitle = "تأكد من كتابة اسم المستخدم بالأحرف الإنجليزية والأرقام بشكل صحيح."
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(results, key = { it.uid }) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            name = user.displayName,
                            avatarUrl = user.avatarUrl,
                            size = 48.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "@${user.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { onBlockUser(user) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text("حظر")
                        }
                        Button(
                            onClick = { onMessageUser(user) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("مراسلة")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
