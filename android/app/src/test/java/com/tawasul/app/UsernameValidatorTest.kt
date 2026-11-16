package com.tawasul.app

import com.tawasul.app.authentication.UsernameValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameValidatorTest {

    @Test
    fun `valid lowercase and mixed-case alphanumeric underscore succeeds and normalizes`() {
        val result = UsernameValidator.validate("  @Ahmad_99 ")
        assertTrue(result is UsernameValidator.ValidationResult.Valid)
        val valid = result as UsernameValidator.ValidationResult.Valid
        assertEquals("Ahmad_99", valid.normalized)
        assertEquals("ahmad_99", valid.lower)
    }

    @Test
    fun `short or oversized username is rejected`() {
        assertTrue(UsernameValidator.validate("ab") is UsernameValidator.ValidationResult.Invalid)
        assertTrue(UsernameValidator.validate("a".repeat(21)) is UsernameValidator.ValidationResult.Invalid)
    }

    @Test
    fun `arabic or special characters are rejected`() {
        assertTrue(UsernameValidator.validate("أحمد_1") is UsernameValidator.ValidationResult.Invalid)
        assertTrue(UsernameValidator.validate("user.name") is UsernameValidator.ValidationResult.Invalid)
        assertTrue(UsernameValidator.validate("user-name") is UsernameValidator.ValidationResult.Invalid)
    }

    @Test
    fun `reserved system usernames are rejected regardless of case`() {
        assertTrue(UsernameValidator.validate("ADMIN") is UsernameValidator.ValidationResult.Invalid)
        assertTrue(UsernameValidator.validate("Tawasul") is UsernameValidator.ValidationResult.Invalid)
    }
}
