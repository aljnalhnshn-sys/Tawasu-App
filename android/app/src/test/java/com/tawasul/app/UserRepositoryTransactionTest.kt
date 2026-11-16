package com.tawasul.app

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import com.tawasul.app.repositories.UserRepository
import com.tawasul.app.repositories.UsernameAlreadyTakenException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
        val existingSnap = mockk<DocumentSnapshot>()

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
                Tasks.forResult(Unit)
            } catch (e: Exception) {
                Tasks.forException(e)
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
}
