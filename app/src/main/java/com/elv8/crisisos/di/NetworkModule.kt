package com.elv8.crisisos.di

import com.elv8.crisisos.BuildConfig
import com.elv8.crisisos.data.remote.api.AcledApi
import com.elv8.crisisos.data.remote.api.GdeltApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideJson(): Json = lenientJson

    @Provides
    @Singleton
    @Named("base")
    fun provideBaseOkHttp(): OkHttpClient {
        val log = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(log)
            .build()
    }

    @Provides
    @Singleton
    @Named("acled")
    fun provideAcledOkHttp(@Named("base") base: OkHttpClient): OkHttpClient {
        // ACLED requires email + key on every request (v2 read endpoint).
        return base.newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url.newBuilder()
                    .addQueryParameter("email", BuildConfig.ACLED_EMAIL)
                    .addQueryParameter("key", BuildConfig.ACLED_KEY)
                    .addQueryParameter("limit", "50")
                    .build()
                chain.proceed(original.newBuilder().url(url).build())
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideGdeltApi(@Named("base") client: OkHttpClient, json: Json): GdeltApi {
        val baseUrl = BuildConfig.GDELT_BASE_URL.toHttpUrlOrNull()
            ?: "https://api.gdeltproject.org/api/v2/".toHttpUrlOrNull()!!
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(GdeltApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAcledApi(@Named("acled") client: OkHttpClient, json: Json): AcledApi {
        val baseUrl = BuildConfig.ACLED_BASE_URL.toHttpUrlOrNull()
            ?: "https://api.acleddata.com/".toHttpUrlOrNull()!!
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(AcledApi::class.java)
    }
}
