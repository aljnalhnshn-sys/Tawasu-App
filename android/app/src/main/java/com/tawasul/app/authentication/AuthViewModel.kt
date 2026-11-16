package com.tawasul.app.authentication

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.tawasul.app.models.UserProfile
import com.tawasul.app.repositories.AuthRepository
import com.tawasul.app.repositories.UserRepository
import com.tawasul.app.repositories.UsernameAlreadyTakenException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed interface AuthDestination {
    data object Loading : AuthDestination
    data object Welcome : AuthDestination
    data object ProfileSetup : AuthDestination
    data object MainApp : AuthDestination
}

data class AuthUiState(
    val destination: AuthDestination = AuthDestination.Loading,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val currentProfile: UserProfile? = null,
    val suggestedDisplayName: String = "",
    val suggestedAvatarUrl: String = "",
    val usernameAvailabilityHint: String? = null,
    val isUsernameAvailable: Boolean? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val googleCredentialHelper: GoogleCredentialHelper,
    private val firebaseMessaging: FirebaseMessaging
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authStateFlow.collect { firebaseUser ->
                if (firebaseUser == null) {
                    _uiState.update {
                        AuthUiState(destination = AuthDestination.Welcome)
                    }
                } else {
                    refreshProfileState(
                        uid = firebaseUser.uid,
                        fallbackName = firebaseUser.displayName.orEmpty(),
                        fallbackPhoto = firebaseUser.photoUrl?.toString().orEmpty()
                    )
                }
            }
        }
    }

    private suspend fun refreshProfileState(
        uid: String,
        fallbackName: String,
        fallbackPhoto: String
    ) {
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        val profile = userRepository.getUserProfile(uid)
        if (profile != null && profile.profileCompleted) {
            syncFcmToken(uid)
            userRepository.updatePresence(uid, true)
            _uiState.update {
                it.copy(
                    destination = AuthDestination.MainApp,
                    currentProfile = profile,
                    isBusy = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    destination = AuthDestination.ProfileSetup,
                    suggestedDisplayName = fallbackName,
                    suggestedAvatarUrl = fallbackPhoto,
                    currentProfile = null,
                    isBusy = false
                )
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            when (val credResult = googleCredentialHelper.requestGoogleIdToken(activityContext)) {
                is GoogleCredentialHelper.CredentialResult.Success -> {
                    authRepository.signInWithGoogleIdToken(credResult.idToken)
                        .onFailure { err ->
                            _uiState.update {
                                it.copy(
                                    isBusy = false,
                                    errorMessage = err.localizedMessage ?: "فشل تسجيل الدخول عبر Google."
                                )
                            }
                        }
                }
                is GoogleCredentialHelper.CredentialResult.Cancelled -> {
                    _uiState.update { it.copy(isBusy = false) }
                }
                is GoogleCredentialHelper.CredentialResult.Error -> {
                    _uiState.update {
                        it.copy(isBusy = false, errorMessage = credResult.messageAr)
                    }
                }
            }
        }
    }

    fun checkUsernameAvailability(rawUsername: String) {
        when (val validation = UsernameValidator.validate(rawUsername)) {
            is UsernameValidator.ValidationResult.Invalid -> {
                _uiState.update {
                    it.copy(
                        isUsernameAvailable = false,
                        usernameAvailabilityHint = validation.messageAr
                    )
                }
            }
            is UsernameValidator.ValidationResult.Valid -> {
                viewModelScope.launch {
                    userRepository.isUsernameAvailable(validation.normalized)
                        .onSuccess { available ->
                            _uiState.update {
                                it.copy(
                                    isUsernameAvailable = available,
                                    usernameAvailabilityHint = if (available) {
                                        "اسم المستخدم @${validation.lower} متاح."
                                    } else {
                                        "اسم المستخدم @${validation.lower} محجوز بالفعل."
                                    }
                                )
                            }
                        }
                        .onFailure { err ->
                            _uiState.update {
                                it.copy(
                                    isUsernameAvailable = null,
                                    usernameAvailabilityHint = err.localizedMessage
                                )
                            }
                        }
                }
            }
        }
    }

    fun completeProfileSetup(displayName: String, rawUsername: String, bio: String) {
        val firebaseUser = authRepository.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            userRepository.completeProfileSetup(
                uid = firebaseUser.uid,
                email = firebaseUser.email.orEmpty(),
                displayName = displayName,
                rawUsername = rawUsername,
                avatarUrl = firebaseUser.photoUrl?.toString().orEmpty(),
                bio = bio
            ).onSuccess { profile ->
                syncFcmToken(firebaseUser.uid)
                _uiState.update {
                    it.copy(
                        destination = AuthDestination.MainApp,
                        currentProfile = profile,
                        isBusy = false
                    )
                }
            }.onFailure { err ->
                val message = when (err) {
                    is UsernameAlreadyTakenException -> "اسم المستخدم هذا محجوز لمستخدم آخر، اختر غيره."
                    else -> err.localizedMessage ?: "تعذر حفظ الملف الشخصي."
                }
                _uiState.update { it.copy(isBusy = false, errorMessage = message) }
            }
        }
    }

    fun signOut() {
        val uid = authRepository.currentUser?.uid
        viewModelScope.launch {
            if (uid != null) userRepository.updatePresence(uid, false)
            authRepository.signOut()
        }
    }

    fun deleteAccount(onError: (String) -> Unit) {
        val uid = authRepository.currentUser?.uid ?: return
        val usernameLower = uiState.value.currentProfile?.usernameLower.orEmpty()
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            userRepository.deleteOwnedAccountData(uid, usernameLower)
                .onSuccess {
                    authRepository.deleteCurrentFirebaseUser()
                        .onFailure { err ->
                            _uiState.update { it.copy(isBusy = false) }
                            onError(
                                err.localizedMessage
                                    ?: "يتطلب حذف الحساب تسجيل الدخول مجددًا للتحقق الأمني."
                            )
                        }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isBusy = false) }
                    onError(err.localizedMessage ?: "تعذر حذف بيانات الحساب.")
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private suspend fun syncFcmToken(uid: String) {
        runCatching {
            val token = firebaseMessaging.token.await()
            userRepository.registerFcmToken(uid, token)
        }
    }
}
