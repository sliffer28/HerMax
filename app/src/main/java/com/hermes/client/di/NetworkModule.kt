package com.hermes.client.di

import android.content.Context
import com.hermes.client.data.security.SecureStorage
import com.hermes.client.hermes.api.HermesApiService
import com.hermes.client.hermes.api.HermesClient
import com.hermes.client.hermes.api.RestHermesClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(secureStorage: SecureStorage): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            // Redact sensitive headers
            redactHeader("Authorization")
            redactHeader("X-Hermes-Session-Key")
        }

        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val apiKey = secureStorage.getApiKey()
                val request = chain.request().newBuilder().apply {
                    if (!apiKey.isNullOrBlank()) {
                        addHeader("Authorization", "Bearer $apiKey")
                    }
                }.build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @javax.inject.Named("cloudOkHttpClient")
    fun provideCloudOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
            redactHeader("Authorization")
            redactHeader("x-api-key")
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
        secureStorage: SecureStorage
    ): Retrofit {
        val rawUrl = secureStorage.getServerUrl()?.trim()
        val validUrl = try {
            val withScheme = when {
                rawUrl.isNullOrBlank() -> "http://192.168.0.197:8642"
                !rawUrl.startsWith("http://") && !rawUrl.startsWith("https://") -> "http://$rawUrl"
                else -> rawUrl
            }
            val withSlash = if (withScheme.endsWith("/")) withScheme else "$withScheme/"
            withSlash.toHttpUrl()
            withSlash
        } catch (_: Exception) {
            "http://192.168.0.197:8642/"
        }

        return Retrofit.Builder()
            .baseUrl(validUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideHermesApiService(retrofit: Retrofit): HermesApiService {
        return retrofit.create(HermesApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideHermesClient(
        apiService: HermesApiService,
        okHttpClient: OkHttpClient,
        json: Json,
        secureStorage: SecureStorage
    ): HermesClient {
        return RestHermesClient(apiService, okHttpClient, json, secureStorage)
    }
}
