package ma.tany.collect.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ma.tany.collect.core.AppEnvironment
import ma.tany.collect.core.locale.AppLanguage
import ma.tany.collect.core.media.OperationPhotoFiles
import ma.tany.collect.core.preferences.CollectPreferences
import ma.tany.collect.core.storage.KeystoreSessionStore
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.CollectAuthRepository
import ma.tany.core.network.CollectRepository
import ma.tany.core.network.DefaultCollectAuthRepository
import ma.tany.core.network.DefaultCollectRepository
import ma.tany.core.network.NoopPushTokenRegistrar
import ma.tany.core.network.PushTokenRegistrar
import ma.tany.core.network.SessionManager
import ma.tany.core.network.TanyCollectApi
import ma.tany.core.network.TanyHttp
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun endpoint(): ApiEndpoint = AppEnvironment.endpoint

    @Provides
    @Singleton
    fun sessionManager(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): SessionManager =
        SessionManager(KeystoreSessionStore(context), scope)

    @Provides
    @Singleton
    fun api(endpoint: ApiEndpoint, session: SessionManager): TanyCollectApi {
        val client = TanyHttp.okHttpClient(endpoint, session, session, AppLanguage, AppEnvironment.userAgent)
        return TanyHttp.retrofit(endpoint, client).create(TanyCollectApi::class.java)
    }

    /** Android push is pending backend gap A-1 (APNs-only today). */
    @Provides
    @Singleton
    fun pushTokenRegistrar(): PushTokenRegistrar = NoopPushTokenRegistrar

    @Provides
    @Singleton
    fun authRepository(api: TanyCollectApi, session: SessionManager, push: PushTokenRegistrar): CollectAuthRepository =
        DefaultCollectAuthRepository(api, session, push)

    @Provides
    @Singleton
    fun collectRepository(api: TanyCollectApi): CollectRepository = DefaultCollectRepository(api)

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): CollectPreferences = CollectPreferences(context)

    @Provides
    @Singleton
    fun photoFiles(@ApplicationContext context: Context): OperationPhotoFiles = OperationPhotoFiles(context)
}
