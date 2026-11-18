package com.tawasul.app

import com.google.android.gms.tasks.Task
import io.mockk.every
import io.mockk.mockk

internal inline fun <reified T> completedTask(value: T): Task<T> =
    mockk(relaxed = true) {
        every { isComplete } returns true
        every { isCanceled } returns false
        every { exception } returns null
        every { result } returns value
    }

internal inline fun <reified T> failedTask(error: Exception): Task<T> =
    mockk(relaxed = true) {
        every { isComplete } returns true
        every { isCanceled } returns false
        every { exception } returns error
    }
