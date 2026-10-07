package ma.tany.core.network

import kotlinx.coroutines.test.runTest
import ma.tany.core.model.common.DeviceRegistrationBody
import ma.tany.core.model.common.DeviceTokenBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Token lifecycle: sign-in, launch refresh (no duplicates), language change, token rotation, logout, 401. */
class PushTokenRegistrarTest {
    private val registered = mutableListOf<String>()
    private val unregistered = mutableListOf<String>()
    private var language = "fr"
    private var fcmToken: String? = "fcm:first_0123456789abcdefghijklmnop"
    private var failRegister = false

    private val registrar = BackendPushTokenRegistrar(
        source = object : PushTokenSource {
            override suspend fun currentToken() = fcmToken
        },
        registerCall = { body: DeviceRegistrationBody ->
            if (failRegister) throw java.io.IOException("offline")
            assertEquals("android", body.platform)
            registered += "${body.token}|$language"
        },
        unregisterCall = { body: DeviceTokenBody -> unregistered += body.token },
        language = { language },
    )

    @Test
    fun signInAlwaysRegistersAndRefreshNeverDuplicates() = runTest {
        registrar.register()
        registrar.refresh()
        registrar.refresh()
        assertEquals(listOf("fcm:first_0123456789abcdefghijklmnop|fr"), registered)
        registrar.register()
        assertEquals(2, registered.size)
    }

    @Test
    fun languageChangeReRegisters() = runTest {
        registrar.refresh()
        language = "ar"
        registrar.refresh()
        assertEquals(listOf("fcm:first_0123456789abcdefghijklmnop|fr", "fcm:first_0123456789abcdefghijklmnop|ar"), registered)
    }

    @Test
    fun rotatedTokenReplacesTheOldOneAndIsUnregisteredOnLogout() = runTest {
        registrar.register()
        registrar.onNewToken("fcm:second_0123456789abcdefghijklmnop")
        registrar.onNewToken("fcm:second_0123456789abcdefghijklmnop")
        assertEquals(2, registered.size)
        assertTrue(registered.last().startsWith("fcm:second"))
        registrar.unregister()
        assertEquals(listOf("fcm:second_0123456789abcdefghijklmnop"), unregistered)
    }

    @Test
    fun failedRegistrationIsRetriedOnNextRefreshAndForgetResets() = runTest {
        failRegister = true
        registrar.refresh()
        assertTrue(registered.isEmpty())
        failRegister = false
        registrar.refresh()
        assertEquals(1, registered.size)
        registrar.forget()
        registrar.refresh()
        assertEquals(2, registered.size)
    }

    @Test
    fun noFirebaseMeansNoCall() = runTest {
        fcmToken = null
        registrar.register()
        registrar.unregister()
        assertTrue(registered.isEmpty() && unregistered.isEmpty())
        assertFalse(BackendPushTokenRegistrar(NoPushTokenSource, {}, {}).isSupported)
    }
}
