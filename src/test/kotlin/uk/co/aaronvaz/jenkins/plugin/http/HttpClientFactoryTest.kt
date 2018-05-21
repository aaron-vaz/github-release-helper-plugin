package uk.co.aaronvaz.jenkins.plugin.http

import com.nhaarman.mockito_kotlin.mock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.Proxy

class HttpClientFactoryTest
{
    @Test
    fun buildHttpClient_NoProxy_HttpClientWithoutProxyCreated()
    {
        // given
        val proxy = null

        // when
        val client = HttpClientFactory.buildHttpClient(proxy)

        // then
        assertNotNull(client)
        assertNull(client?.proxy)
    }

    @Test
    fun buildHttpClient_ValidProxy_HttpClientWithoutProxyCreated()
    {
        // given
        val proxy: Proxy = mock()

        // when
        val client = HttpClientFactory.buildHttpClient(proxy)

        // then
        assertNotNull(client)
        assertEquals(proxy, client?.proxy)
    }

    @Test
    fun buildHttpClient_ClientAlreadyCreated_CachedClientReturned()
    {
        // given
        val cachedClient = HttpClientFactory.buildHttpClient(null)

        // when
        val client = HttpClientFactory.buildHttpClient(null)

        // then
        assertEquals(cachedClient, client)
    }
}