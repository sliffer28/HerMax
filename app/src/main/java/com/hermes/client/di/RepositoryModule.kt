package com.hermes.client.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.hermes.client.data.local.ConversationDao
import com.hermes.client.data.local.MessageDao
import com.hermes.client.data.local.ServerProfileDao
import com.hermes.client.data.repository.ConversationRepositoryImpl
import com.hermes.client.data.repository.HermesRepositoryImpl
import com.hermes.client.data.repository.ServerProfileRepositoryImpl
import com.hermes.client.data.security.SecureStorage
import com.hermes.client.domain.repository.ConversationRepository
import com.hermes.client.domain.repository.HermesRepository
import com.hermes.client.domain.repository.ServerProfileRepository
import com.hermes.client.hermes.api.HermesClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSecureStorage(@ApplicationContext context: Context): SecureStorage {
        return SecureStorage(context)
    }

    @Provides
    @Singleton
    fun provideHermesRepository(
        client: HermesClient
    ): HermesRepository {
        return HermesRepositoryImpl(client)
    }

    @Provides
    @Singleton
    fun provideConversationRepository(
        conversationDao: ConversationDao,
        messageDao: MessageDao
    ): ConversationRepository {
        return ConversationRepositoryImpl(conversationDao, messageDao)
    }

    @Provides
    @Singleton
    fun provideServerProfileRepository(
        serverProfileDao: ServerProfileDao,
        dataStore: DataStore<Preferences>
    ): ServerProfileRepository {
        return ServerProfileRepositoryImpl(serverProfileDao, dataStore)
    }
}
