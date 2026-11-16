package com.tawasul.app.authentication

object UsernameValidator {
    private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{3,20}$")
    private val RESERVED_USERNAMES = setOf(
        "admin", "administrator", "support", "tawasul", "system", "root", "help", "moderator"
    )

    sealed class ValidationResult {
        data class Valid(val normalized: String, val lower: String) : ValidationResult()
        data class Invalid(val messageAr: String) : ValidationResult()
    }

    fun validate(rawInput: String): ValidationResult {
        val trimmed = rawInput.trim().removePrefix("@")
        if (trimmed.length < 3 || trimmed.length > 20) {
            return ValidationResult.Invalid("يجب أن يكون اسم المستخدم بين 3 و20 حرفًا.")
        }
        if (!USERNAME_REGEX.matches(trimmed)) {
            return ValidationResult.Invalid(
                "يُسمح فقط بالأحرف الإنجليزية (a-z) والأرقام (0-9) والشرطة السفلية (_)."
            )
        }
        val lower = trimmed.lowercase()
        if (lower in RESERVED_USERNAMES) {
            return ValidationResult.Invalid("اسم المستخدم هذا محجوز للنظام، اختر اسمًا آخر.")
        }
        return ValidationResult.Valid(normalized = trimmed, lower = lower)
    }
}
