/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.matrix.androidsdk

import com.facebook.stetho.okhttp3.StethoInterceptor
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.matrix.androidsdk.core.BuildConfig
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.interceptors.CurlLoggingInterceptor
import org.matrix.androidsdk.core.interceptors.FormattedJsonHttpLogger
import org.matrix.androidsdk.rest.client.MXRestExecutorService
import org.matrix.androidsdk.ssl.CertUtil
import java.util.concurrent.TimeUnit


class RestClientHttpClientFactory(private val testInterceptor: Interceptor? = null) {

    companion object {
        const val READ_TIMEOUT_MS = 60_000L
        const val WRITE_TIMEOUT_MS = 60_000L
    }

    fun createHttpClient(hsConfig: HomeServerConnectionConfig,
                         endPoint: String,
                         authenticationInterceptor: Interceptor): OkHttpClient {
        val okHttpClientBuilder = OkHttpClient().newBuilder()
                .connectTimeout(RestClient.CONNECTION_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .addInterceptor(authenticationInterceptor)

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor(FormattedJsonHttpLogger())
            loggingInterceptor.level = BuildConfig.OKHTTP_LOGGING_LEVEL


            okHttpClientBuilder
                    .addInterceptor(loggingInterceptor)
                    .addNetworkInterceptor(StethoInterceptor())
                    .addInterceptor(CurlLoggingInterceptor())
                    .apply {
                        if (testInterceptor != null) {
                            addInterceptor(testInterceptor)
                        }
                    }

        }


        if (RestClient.mUseMXExecutor) {
            okHttpClientBuilder.dispatcher(Dispatcher(MXRestExecutorService()))
        }

        try {
            val pair = CertUtil.newPinnedSSLSocketFactory(hsConfig)
            okHttpClientBuilder.sslSocketFactory(pair.first, pair.second)
            okHttpClientBuilder.hostnameVerifier(CertUtil.newHostnameVerifier(hsConfig))
            okHttpClientBuilder.connectionSpecs(CertUtil.newConnectionSpecs(hsConfig, endPoint))
        } catch (e: Exception) {
            Log.e(RestClientHttpClientFactory::class.java.name, "## RestClient() setSslSocketFactory failed: " + e.message, e)
        }


        return okHttpClientBuilder.build()
    }

}