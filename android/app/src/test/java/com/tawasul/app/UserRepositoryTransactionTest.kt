package com.tawasul.app

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.tawasul.app.repositories.UserRepository
import com.tawasul.app.repositories.UsernameAlreadyTakenException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRepositoryTransactionTest {

    @Test
    fun `completeProfileSetup fails when username is already owned by another uid`() = runTest {
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        val usernamesCol = mockk<CollectionReference>(relaxed = true)
        val usersCol = mockk<CollectionReference>(relaxed = true)
        val usernameDocRef = mockk<DocumentReference>(relaxed = true)
        val userDocRef = mockk<DocumentReference>(relaxed = true)
        val existingSnap = mockk<DocumentSnapshot>(relaxed = true)

        every { firestore.collection("usernames") } returns usernamesCol
        every { firestore.collection("users") } returns usersCol
        every { usernamesCol.document("taken_user") } returns usernameDocRef
        every { usersCol.document("uid_attacker") } returns userDocRef
        every { existingSnap.exists() } returns true
        every { existingSnap.getString("uid") } returns "uid_original_owner"

        every { firestore.runTransaction<Unit>(any()) } answers {
            val fn = firstArg<Transaction.Function<Unit>>()
            val tx = mockk<Transaction>(relaxed = true)
            every { tx.get(usernameDocRef) } returns existingSnap
            try {
                fn.apply(tx)
                completedTask(Unit)
            } catch (e: Exception) {
                failedTask(e)
            }
        }

        val repo = UserRepository(firestore)
        val result = repo.completeProfileSetup(
            uid = "uid_attacker",
            email = "attacker@example.com",
            displayName = "Attacker",
            rawUsername = "Taken_User",
            avatarUrl = ""
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UsernameAlreadyTakenException)
    }

    @Test
    fun `completeProfileSetup reserves username and writes public and private docs when available`() = runTest {
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        val usernamesCol = mockk<CollectionReference>(relaxed = true)
        val usersCol = mockk<CollectionReference>(relaxed = true)
        val usernameDocRef = mockk<DocumentReference>(relaxed = true)
        val userDocRef = mockk<DocumentReference>(relaxed = true)
        val privateCol = mockk<CollectionReference>(relaxed = true)
        val privateDocRef = mockk<DocumentReference>(relaxed = true)
        val availableSnap = mockk<DocumentSnapshot>(relaxed = true)
        val tx = mockk<Transaction>(relaxed = true)

        every { firestore.collection("usernames") } returns usernamesCol
        every { firestore.collection("users") } returns usersCol
        every { usernamesCol.document("ahmad_99") } returns usernameDocRef
        every { usersCol.document("uid_ahmad") } returns userDocRef
        every { userDocRef.collection("private") } returns privateCol
        every { privateCol.document("info") } returns privateDocRef
        every { availableSnap.exists() } returns false
        every { tx.get(usernameDocRef) } returns availableSnap

        every { firestore.runTransaction<Unit>(any()) } answers {
            val fn = firstArg<Transaction.Function<Unit>>()
            try {
                fn.apply(tx)
                completedTask(Unit)
            } catch (e: Exception) {
                failedTask(e)
            }
        }

        val repo = UserRepository(firestore)
        val result = repo.completeProfileSetup(
            uid = "uid_ahmad",
            email = "ahmad@example.com",
            displayName = "أحمد الوادعي",
            rawUsername = "@Ahmad_99",
            avatarUrl = "https://example.com/a.jpg",
            bio = "مطور تطبيقات"
        )

        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals("Ahmad_99", profile.username)
        assertEquals("ahmad_99", profile.usernameLower)
        assertEquals("أحمد الوادعي", profile.displayName)
        verify(exactly = 1) { tx.set(usernameDocRef, any()) }
        verify(exactly = 1) { tx.set(userDocRef, any()) }
        verify(exactly = 1) { tx.set(privateDocRef, any(), SetOptions.merge()) }
    }

    @Test
    fun `completeProfileSetup rejects reserved username before starting transaction`() = runTest {
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        val repo = UserRepository(firestore)

        val result = repo.completeProfileSetup(
            uid = "uid_1",
            email = "a@b.com",
            displayName = "Tester",
            rawUsername = "admin",
            avatarUrl = ""
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        verify(exactly = 0) { firestore.runTransaction<Unit>(any()) }
    }

    @Test
    fun `updateProfile releases old username reservation and reserves new username atomically`() = runTest {
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        val usernamesCol = mockk<CollectionReference>(relaxed = true)
        val usersCol = mockk<CollectionReference>(relaxed = true)
        val oldUsernameRef = mockk<DocumentReference>(relaxed = true)
        val newUsernameRef = mockk<DocumentReference>(relaxed = true)
        val userDocRef = mockk<DocumentReference>(relaxed = true)
        val oldSnap = mockk<DocumentSnapshot>(relaxed = true)
        val newSnap = mockk<DocumentSnapshot>(relaxed = true)
        val tx = mockk<Transaction>(relaxed = true)

        every { firestore.collection("usernames") } returns usernamesCol
        every { firestore.collection("users") } returns usersCol
        every { usernamesCol.document("old_name") } returns oldUsernameRef
        every { usernamesCol.document("new_name") } returns newUsernameRef
        every { usersCol.document("uid_1") } returns userDocRef
        every { oldSnap.exists() } returns true
        every { oldSnap.getString("uid") } returns "uid_1"
        every { newSnap.exists() } returns false
        every { tx.get(oldUsernameRef) } returns oldSnap
        every { tx.get(newUsernameRef) } returns newSnap

        every { firestore.runTransaction<Unit>(any()) } answers {
            val fn = firstArg<Transaction.Function<Unit>>()
            try {
                fn.apply(tx)
                completedTask(Unit)
            } catch (e: Exception) {
                failedTask(e)
            }
        }

        val repo = UserRepository(firestore)
        val result = repo.updateProfile(
            uid = "uid_1",
            displayName = "الاسم الجديد",
            bio = "نبذة محدثة",
            avatarUrl = "",
            currentUsernameLower = "old_name",
            newRawUsername = "New_Name"
        )

        assertTrue(result.isSuccess)
        verify(exactly = 1) { tx.delete(oldUsernameRef) }
        verify(exactly = 1) { tx.set(newUsernameRef, any()) }
        verify(exactly = 1) { tx.update(userDocRef, any<Map<String, Any>>()) }
    }
}
