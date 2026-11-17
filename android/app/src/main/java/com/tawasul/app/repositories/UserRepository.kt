package com.tawasul.app.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tawasul.app.authentication.UsernameValidator
import com.tawasul.app.data.remote.FirestorePaths
import com.tawasul.app.models.BlockedUser
import com.tawasul.app.models.GroupPrivacySetting
import com.tawasul.app.models.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

class UsernameAlreadyTakenException : IllegalStateException("اسم المستخدم محجوز بالفعل.")

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val usersCol get() = firestore.collection(FirestorePaths.USERS)
    private val usernamesCol get() = firestore.collection(FirestorePaths.USERNAMES)

    suspend fun getUserProfile(uid: String): UserProfile? {
        val snapshot = usersCol.document(uid).get().await()
        return if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val reg = usersCol.document(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(null)
                return@addSnapshotListener
            }
            trySend(snap?.toObject(UserProfile::class.java))
        }
        awaitClose { reg.remove() }
    }

    suspend fun isUsernameAvailable(rawUsername: String): Result<Boolean> = runCatching {
        val validated = UsernameValidator.validate(rawUsername)
        if (validated is UsernameValidator.ValidationResult.Invalid) {
            throw IllegalArgumentException(validated.messageAr)
        }
        val lower = (validated as UsernameValidator.ValidationResult.Valid).lower
        !usernamesCol.document(lower).get().await().exists()
    }

    /**
     * Atomically reserves `usernames/{usernameLower}` and writes public + private user docs.
     * Prevents race conditions and duplicate usernames across concurrent requests.
     */
    suspend fun completeProfileSetup(
        uid: String,
        email: String,
        displayName: String,
        rawUsername: String,
        avatarUrl: String,
        bio: String = ""
    ): Result<UserProfile> = runCatching {
        val cleanName = displayName.trim()
        require(cleanName.length in 2..50) { "يجب أن يكون الاسم الظاهر بين 2 و50 حرفًا." }

        val validation = UsernameValidator.validate(rawUsername)
        val valid = when (validation) {
            is UsernameValidator.ValidationResult.Valid -> validation
            is UsernameValidator.ValidationResult.Invalid -> throw IllegalArgumentException(validation.messageAr)
        }

        val userRef = usersCol.document(uid)
        val privateRef = userRef
            .collection(FirestorePaths.PRIVATE_SUBCOLLECTION)
            .document(FirestorePaths.PRIVATE_INFO_DOC)
        val usernameRef = usernamesCol.document(valid.lower)

        firestore.runTransaction<Unit> { tx ->
            val existingReservation = tx.get(usernameRef)
            if (existingReservation.exists() && existingReservation.getString("uid") != uid) {
                throw UsernameAlreadyTakenException()
            }

            tx.set(
                usernameRef,
                mapOf(
                    "uid" to uid,
                    "usernameLower" to valid.lower,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            val publicPayload = mapOf(
                "uid" to uid,
                "username" to valid.normalized,
                "usernameLower" to valid.lower,
                "displayName" to cleanName,
                "bio" to bio.trim().take(160),
                "avatarUrl" to avatarUrl,
                "isOnline" to true,
                "lastSeen" to FieldValue.serverTimestamp(),
                "showOnlineStatus" to true,
                "showReadReceipts" to true,
                "whoCanAddToGroups" to GroupPrivacySetting.EVERYONE.name,
                "profileCompleted" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            tx.set(userRef, publicPayload)

            val privatePayload = mapOf(
                "uid" to uid,
                "email" to email,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            tx.set(privateRef, privatePayload, SetOptions.merge())
            Unit
        }.await()

        UserProfile(
            uid = uid,
            username = valid.normalized,
            usernameLower = valid.lower,
            displayName = cleanName,
            bio = bio.trim().take(160),
            avatarUrl = avatarUrl,
            isOnline = true,
            profileCompleted = true,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
    }

    /**
     * Atomically updates username while releasing the old reservation only if owned by `uid`.
     */
    suspend fun updateProfile(
        uid: String,
        displayName: String,
        bio: String,
        avatarUrl: String,
        currentUsernameLower: String,
        newRawUsername: String? = null
    ): Result<Unit> = runCatching {
        val cleanName = displayName.trim()
        require(cleanName.length in 2..50) { "يجب أن يكون الاسم الظاهر بين 2 و50 حرفًا." }

        val targetUsername = if (newRawUsername != null) {
            when (val res = UsernameValidator.validate(newRawUsername)) {
                is UsernameValidator.ValidationResult.Valid -> res
                is UsernameValidator.ValidationResult.Invalid -> throw IllegalArgumentException(res.messageAr)
            }
        } else null

        val userRef = usersCol.document(uid)

        firestore.runTransaction<Unit> { tx ->
            if (targetUsername != null && targetUsername.lower != currentUsernameLower) {
                val newReservationRef = usernamesCol.document(targetUsername.lower)
                val newSnap = tx.get(newReservationRef)
                if (newSnap.exists() && newSnap.getString("uid") != uid) {
                    throw UsernameAlreadyTakenException()
                }
                val oldReservationRef = usernamesCol.document(currentUsernameLower)
                val oldSnap = tx.get(oldReservationRef)
                if (oldSnap.exists() && oldSnap.getString("uid") == uid) {
                    tx.delete(oldReservationRef)
                }
                tx.set(
                    newReservationRef,
                    mapOf(
                        "uid" to uid,
                        "usernameLower" to targetUsername.lower,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val updates = mutableMapOf<String, Any>(
                "displayName" to cleanName,
                "bio" to bio.trim().take(160),
                "avatarUrl" to avatarUrl,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (targetUsername != null) {
                updates["username"] = targetUsername.normalized
                updates["usernameLower"] = targetUsername.lower
            }
            tx.update(userRef, updates)
            Unit
        }.await()
    }

    suspend fun searchUsersByUsername(
        query: String,
        currentUid: String,
        blockedUids: Set<String> = emptySet()
    ): Result<List<UserProfile>> = runCatching {
        val normalized = query.trim().removePrefix("@").lowercase()
        if (normalized.isEmpty()) return@runCatching emptyList()

        val prefixSnapshot = usersCol
            .whereGreaterThanOrEqualTo("usernameLower", normalized)
            .whereLessThanOrEqualTo("usernameLower", "$normalized\uf8ff")
            .limit(20)
            .get()
            .await()

        prefixSnapshot.documents
            .mapNotNull { it.toObject(UserProfile::class.java) }
            .filter { it.uid != currentUid && it.uid !in blockedUids && it.profileCompleted }
    }

    suspend fun updatePresence(uid: String, isOnline: Boolean) {
        runCatching {
            usersCol.document(uid).update(
                mapOf(
                    "isOnline" to isOnline,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun updatePrivacySettings(
        uid: String,
        showOnlineStatus: Boolean,
        showReadReceipts: Boolean,
        whoCanAddToGroups: GroupPrivacySetting
    ): Result<Unit> = runCatching {
        usersCol.document(uid).update(
            mapOf(
                "showOnlineStatus" to showOnlineStatus,
                "showReadReceipts" to showReadReceipts,
                "whoCanAddToGroups" to whoCanAddToGroups.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun registerFcmToken(uid: String, token: String) {
        if (uid.isBlank() || token.isBlank()) return
        runCatching {
            usersCol.document(uid)
                .collection(FirestorePaths.PRIVATE_SUBCOLLECTION)
                .document(FirestorePaths.PRIVATE_INFO_DOC)
                .set(
                    mapOf(
                        "uid" to uid,
                        "fcmTokens" to FieldValue.arrayUnion(token),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        }
    }

    suspend fun blockUser(currentUid: String, target: UserProfile): Result<Unit> = runCatching {
        usersCol.document(currentUid)
            .collection(FirestorePaths.BLOCKED_SUBCOLLECTION)
            .document(target.uid)
            .set(
                mapOf(
                    "blockedUid" to target.uid,
                    "blockedUsername" to target.username,
                    "blockedDisplayName" to target.displayName,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun unblockUser(currentUid: String, targetUid: String): Result<Unit> = runCatching {
        usersCol.document(currentUid)
            .collection(FirestorePaths.BLOCKED_SUBCOLLECTION)
            .document(targetUid)
            .delete()
            .await()
    }

    fun observeBlockedUsers(currentUid: String): Flow<List<BlockedUser>> = callbackFlow {
        val reg = usersCol.document(currentUid)
            .collection(FirestorePaths.BLOCKED_SUBCOLLECTION)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(BlockedUser::class.java) }.orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun isBlockedBetween(uidA: String, uidB: String): Boolean {
        val aBlockedB = usersCol.document(uidA)
            .collection(FirestorePaths.BLOCKED_SUBCOLLECTION)
            .document(uidB).get().await().exists()
        if (aBlockedB) return true
        return usersCol.document(uidB)
            .collection(FirestorePaths.BLOCKED_SUBCOLLECTION)
            .document(uidA).get().await().exists()
    }

    /**
     * Deletes user profile, private PII, blocked list, and username reservation cleanly.
     * Note: Shared chats remain for the other participant while personal references are cleaned.
     */
    suspend fun deleteOwnedAccountData(uid: String, usernameLower: String): Result<Unit> = runCatching {
        val userRef = usersCol.document(uid)
        val blockedDocs = userRef.collection(FirestorePaths.BLOCKED_SUBCOLLECTION).get().await()
        val batch = firestore.batch()
        blockedDocs.documents.forEach { batch.delete(it.reference) }
        batch.delete(
            userRef.collection(FirestorePaths.PRIVATE_SUBCOLLECTION)
                .document(FirestorePaths.PRIVATE_INFO_DOC)
        )
        if (usernameLower.isNotBlank()) {
            batch.delete(usernamesCol.document(usernameLower))
        }
        batch.delete(userRef)
        batch.commit().await()
    }
}
