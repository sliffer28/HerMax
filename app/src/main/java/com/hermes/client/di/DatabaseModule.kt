package com.hermes.client.di

import android.content.Context
import androidx.room.Room
import com.hermes.client.data.local.ConversationDao
import com.hermes.client.data.local.HermesDatabase
import com.hermes.client.data.local.MessageDao
import com.hermes.client.data.local.ServerProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HermesDatabase {
        return Room.databaseBuilder(
            context,
            HermesDatabase::class.java,
            "hermes_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideConversationDao(database: HermesDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    fun provideMessageDao(database: HermesDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    fun provideServerProfileDao(database: HermesDatabase): ServerProfileDao {
        return database.serverProfileDao()
    }
}
