package com.tawasul.app.repositories

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: FirebaseStorage
) {
    companion object {
        const val MAX_IMAGE_OR_AUDIO_BYTES = 10L * 1024L * 1024L // 10 MB
        const val MAX_VIDEO_BYTES = 25L * 1024L * 1024L // 25 MB
    }

    suspend fun uploadAvatar(uid: String, imageUri: Uri): Result<String> =
        uploadFile(
            uri = imageUri,
            storagePath = "avatars/$uid/profile_${UUID.randomUUID()}.jpg",
            maxBytes = MAX_IMAGE_OR_AUDIO_BYTES
        )

    suspend fun uploadChatMedia(
        chatId: String,
        senderUid: String,
        uri: Uri,
        folder: String,
        extension: String,
        isVideo: Boolean = false
    ): Result<String> =
        uploadFile(
            uri = uri,
            storagePath = "chats/$chatId/$folder/${senderUid}_${UUID.randomUUID()}.$extension",
            maxBytes = if (isVideo) MAX_VIDEO_BYTES else MAX_IMAGE_OR_AUDIO_BYTES
        )

    suspend fun uploadStatusMedia(
        uid: String,
        uri: Uri,
        extension: String,
        isVideo: Boolean = false
    ): Result<String> =
        uploadFile(
            uri = uri,
            storagePath = "statuses/$uid/${UUID.randomUUID()}.$extension",
            maxBytes = if (isVideo) MAX_VIDEO_BYTES else MAX_IMAGE_OR_AUDIO_BYTES
        )

    private suspend fun uploadFile(
        uri: Uri,
        storagePath: String,
        maxBytes: Long
    ): Result<String> = runCatching {
        val fileSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (fileSize > maxBytes) {
            throw IllegalArgumentException("حجم الملف يتجاوز الحد الأقصى المسموح به.")
        }
        val ref = storage.reference.child(storagePath)
        ref.putFile(uri).await()
        ref.downloadUrl.await().toString()
    }
}
