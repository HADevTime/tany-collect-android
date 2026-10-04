package ma.tany.core.network

import kotlinx.coroutines.CoroutineScope
import okhttp3.mockwebserver.MockWebServer

class InMemorySessionStore(var stored: StoredSession? = null, var failOnRead: Boolean = false) : SessionStore {
    override suspend fun read(): StoredSession? = if (failOnRead) error("corrupted") else stored

    override suspend fun write(session: StoredSession) {
        stored = session
    }

    override suspend fun clear() {
        stored = null
    }
}

/** Builds the real production stack (same interceptors) against a MockWebServer. */
class TestStack(server: MockWebServer, scope: CoroutineScope, store: SessionStore = InMemorySessionStore(), language: String = "ar") {
    val session = SessionManager(store, scope)
    private val endpoint = ApiEndpoint.of(ApiEnvironment.DEV, server.url("/").toString())
    val client = TanyHttp.okHttpClient(endpoint, session, session, { language }, "TANY-Android/test")
    val api: TanyCollectApi = TanyHttp.retrofit(endpoint, client).create(TanyCollectApi::class.java)
}
