package uk.co.aaronvaz.jenkins.plugin.http

import com.squareup.okhttp.Cache
import com.squareup.okhttp.ConnectionPool
import com.squareup.okhttp.Interceptor
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.logging.HttpLoggingInterceptor
import java.io.PrintStream
import java.net.Proxy
import java.nio.file.Files
import java.util.concurrent.TimeUnit

object HttpClientFactory
{
    val clients = mutableMapOf<Proxy?, OkHttpClient>()

    fun buildHttpClient(proxy: Proxy?, logger: PrintStream): OkHttpClient?
    {
        return clients.getOrPut(proxy) {
            val cache = Cache(Files.createTempDirectory(null).toFile(), 5 * 1024 * 1024)
            val pool = ConnectionPool(2, 1, TimeUnit.MINUTES)

            OkHttpClient().apply {
                this.cache = cache
                this.proxy = proxy
                this.connectionPool = pool
                this.setConnectTimeout(5, TimeUnit.MINUTES)
                this.setReadTimeout(5, TimeUnit.MINUTES)
                this.setWriteTimeout(5, TimeUnit.MINUTES)

                this.interceptors()
                    .add(Interceptor { chain ->
                        val request = chain.request()
                        var response = chain.proceed(request)

                        if(!response.isSuccessful)
                        {
                            logger.println("Retrying upload of ${request.httpUrl().queryParameter("name")}")
                            response = chain.proceed(request)
                        }

                        response
                    })

                val httpLoggingInterceptor = HttpLoggingInterceptor(HttpLoggingInterceptor.Logger { logger.println(it) })
                httpLoggingInterceptor.level = HttpLoggingInterceptor.Level.BASIC

                this.interceptors().add(httpLoggingInterceptor)
            }
        }
    }
}