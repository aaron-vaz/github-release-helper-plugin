package uk.co.aaronvaz.jenkins.plugin.http

import com.squareup.okhttp.Cache
import com.squareup.okhttp.OkHttpClient
import java.net.Proxy
import java.nio.file.Files
import java.util.concurrent.TimeUnit

object HttpClientFactory
{
    val clients = mutableMapOf<Proxy?, OkHttpClient>()

    fun buildHttpClient(proxy: Proxy?): OkHttpClient?
    {
        return clients.getOrPut(proxy) {
            val cache = Cache(Files.createTempDirectory(null).toFile(), 5 * 1024 * 1024)
            OkHttpClient().apply {
                this.cache = cache
                this.proxy = proxy
                this.setConnectTimeout(1, TimeUnit.MINUTES)
                this.setReadTimeout(1, TimeUnit.MINUTES)
                this.setWriteTimeout(1, TimeUnit.MINUTES)
            }
        }
    }
}