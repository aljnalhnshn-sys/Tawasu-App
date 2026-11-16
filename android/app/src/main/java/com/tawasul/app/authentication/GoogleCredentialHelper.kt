package com.tawasul.app.authentication

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.tawasul.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleCredentialHelper @Inject constructor() {

    sealed class CredentialResult {
        data class Success(val idToken: String) : CredentialResult()
        data object Cancelled : CredentialResult()
        data class Error(val messageAr: String) : CredentialResult()
    }

    suspend fun requestGoogleIdToken(activityContext: Context): CredentialResult {
        val webClientId = resolveWebClientId(activityContext)
        if (webClientId.isNullOrBlank()) {
            return CredentialResult.Error(
                "لم يتم ضبط Web Client ID؛ تأكد من وضع google-services.json وتفعيل Google Sign-In في Firebase."
            )
        }

        val credentialManager = CredentialManager.create(activityContext)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdCredential = GoogleIdTokenCredential.createFrom(credential.data)
                CredentialResult.Success(googleIdCredential.idToken)
            } else {
                CredentialResult.Error("نوع بيانات الاعتماد غير مدعوم.")
            }
        } catch (_: GetCredentialCancellationException) {
            CredentialResult.Cancelled
        } catch (_: NoCredentialException) {
            CredentialResult.Error("لا يوجد حساب Google متاح على هذا الجهاز لتسجيل الدخول.")
        } catch (e: Exception) {
            CredentialResult.Error(e.localizedMessage ?: "تعذر تسجيل الدخول عبر Google.")
        }
    }

    private fun resolveWebClientId(context: Context): String? {
        val fromConfig = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (fromConfig.isNotBlank() && !fromConfig.startsWith("REPLACE_WITH")) {
            return fromConfig
        }
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resId != 0) {
            val fromRes = context.getString(resId)
            if (fromRes.isNotBlank() && !fromRes.startsWith("REPLACE_WITH")) {
                return fromRes
            }
        }
        return null
    }
}
