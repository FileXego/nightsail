package app.nightsail.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {

    private val launcher = setOf("com.android.launcher3")

    @Test
    fun `self package always allowed`() {
        assertFalse(
            LockPolicy.shouldIntercept("app.nightsail", "app.nightsail", false, false, launcher),
        )
    }

    @Test
    fun `system ui and keyguard always allowed`() {
        assertFalse(LockPolicy.shouldIntercept("com.android.systemui", "app.nightsail", false, false, launcher))
        assertFalse(LockPolicy.shouldIntercept("com.android.keyguard", "app.nightsail", false, false, launcher))
    }

    @Test
    fun `launcher itself allowed, but apps launched from it are not`() {
        // 桌面是走廊：放行
        assertFalse(LockPolicy.shouldIntercept("com.android.launcher3", "app.nightsail", false, false, launcher))
        // 从桌面点开的抖音：锁
        assertTrue(LockPolicy.shouldIntercept("com.ss.android.ugc.aweme", "app.nightsail", false, false, launcher))
    }

    @Test
    fun `whitelisted app allowed`() {
        assertFalse(LockPolicy.shouldIntercept("com.tencent.mm", "app.nightsail", true, false, launcher))
    }

    @Test
    fun `temporary pass allows recent whitelist launch`() {
        assertFalse(LockPolicy.shouldIntercept("com.tencent.mm", "app.nightsail", true, true, launcher))
    }

    @Test
    fun `settings hard blocked`() {
        assertTrue(LockPolicy.shouldHardBlock("com.android.settings"))
        assertFalse(LockPolicy.shouldHardBlock("com.tencent.mm"))
    }
}
