package com.entry.biz.user.ui

import org.junit.Assert.*
import org.junit.Test

class AuthExitPolicyTest {
    @Test fun firstBackStaysAndSecondBackExitsWithinWindow() {
        var now = 0L
        val policy = AuthExitPolicy({ now })
        assertFalse(policy.shouldExit())
        now = 1500
        assertTrue(policy.shouldExit())
        assertFalse(policy.shouldExit())
    }
    @Test fun expiredWindowRequiresAnotherConfirmation() {
        var now = 10L
        val policy = AuthExitPolicy({ now })
        assertFalse(policy.shouldExit())
        now += 2000
        assertFalse(policy.shouldExit())
    }
    @Test fun leavingLoginResetsConfirmation() {
        val policy = AuthExitPolicy({ 100L })
        assertFalse(policy.shouldExit())
        policy.reset()
        assertFalse(policy.shouldExit())
    }
}
