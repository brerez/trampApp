package com.example.tramapp.di

import com.example.tramapp.BuildConfig
import com.example.tramapp.data.remote.GolemioService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.golemio.cz/v2/"

    @Provides
    @Singleton
    fun provideThrottleUtil(): com.example.tramapp.utils.ThrottleUtil {
        return com.example.tramapp.utils.ThrottleUtil()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(throttleUtil: com.example.tramapp.utils.ThrottleUtil): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("x-access-token", BuildConfig.GOLEMIO_API_KEY)
                .build()
            chain.proceed(request)
        }

        val rateLimitInterceptor = Interceptor { chain ->
            throttleUtil.waitForRateLimitBlocking()
            chain.proceed(chain.request())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(rateLimitInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideGolemioService(retrofit: Retrofit): GolemioService {
        return retrofit.create(GolemioService::class.java)
    }
}
