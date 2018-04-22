package uk.co.aaronvaz.jenkins.plugin.callable

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.spy
import com.nhaarman.mockito_kotlin.times
import com.squareup.okhttp.Call
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Response
import hudson.ProxyConfiguration
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.remoting.VirtualChannel
import jenkins.model.Jenkins
import org.junit.Ignore
import org.junit.Test
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GitHub
import org.mockito.BDDMockito.willReturn
import java.io.File
import java.net.Proxy

class GitHubAssetUploadCallableTest
{
    private val mockJenkins: Jenkins = mock()

    private val mockListener: TaskListener = mock()

    private val mockRun: Run<*, *> = mock()

    private val mockRoot: GitHub = mock()

    private val mockRelease: GHRelease = mock()

    private val mockClient: OkHttpClient = mock()

    private val mockCall: Call = mock()

    private val mockResponse: Response = mock()

    private val mockFile: File = mock()

    private val mockVirtualChannel: VirtualChannel = mock()

    private val testApiURL = "https://test.github.com/api/v3"

    private val testUploadURL = "https://test.github.com/uploads/api/{?name,label}"

    private val gitHubAssetUploadCallable = spy(GitHubAssetUploadCallable(mockListener,
                                                                          mockRun,
                                                                          mockRelease,
                                                                          mockClient))

    @Test
    fun invoke_ValidArguments_AssetsUploadedAndBuildSuccessful()
    {
        // given
        setupMocks(true)

        // when
        gitHubAssetUploadCallable.invoke(mockFile, mockVirtualChannel)

        // then
        inOrder(mockClient, mockCall, mockListener, mockRun)
        {
            verify(mockClient, times(0)).proxy = any()
            verify(mockClient).newCall(any())
            verify(mockListener, times(0)).error(any())
            verify(mockRun, times(0)).setResult(Result.FAILURE)
        }
    }

    @Test
    fun invoke_ValidArgumentsAndProxySet_AssetsUploadedUsingProxyServerAndBuildSuccessful()
    {
        // given
        setupMocks(true)

        val mockProxyConfiguration: ProxyConfiguration = mock()
        val mockProxy: Proxy = mock()

        willReturn(mockProxy).given(mockProxyConfiguration).createProxy(any())
        mockJenkins.proxy = mockProxyConfiguration

        // when
        gitHubAssetUploadCallable.invoke(mockFile, mockVirtualChannel)

        // then
        inOrder(mockClient, mockCall, mockListener, mockRun)
        {
            verify(mockClient).proxy = any()
            verify(mockClient).newCall(any())
            verify(mockListener, times(0)).error(any())
            verify(mockRun, times(0)).setResult(Result.FAILURE)
        }
    }

    @Test
    @Ignore("Stubbing response differently causes null pointer")
    fun invoke_ApiRequestFailed_AssetsNotUploadedAndBuildFailed()
    {
        // given
        setupMocks(false)

        // when
        gitHubAssetUploadCallable.invoke(mockFile, mockVirtualChannel)

        // then
        inOrder(mockClient, mockCall, mockListener, mockRelease, mockRun)
        {
            verify(mockClient, times(0)).proxy = any()
            verify(mockClient).newCall(any())
            verify(mockCall).execute()

            verify(mockListener, times(3)).error(any())
            verify(mockRelease).delete()
            verify(mockRun).setResult(Result.FAILURE)
        }
    }

    private fun setupMocks(isResponseSuccessful: Boolean)
    {
        willReturn(testUploadURL).given(mockRelease).uploadUrl
        willReturn(mockRoot).given(mockRelease).root
        willReturn(testApiURL).given(mockRoot).apiUrl

        willReturn(mockJenkins).given(gitHubAssetUploadCallable).getJenkinsInstance()
        willReturn("token").given(gitHubAssetUploadCallable).getApiToken(any())

        willReturn(mockCall).given(mockClient).newCall(any())
        willReturn(mockResponse).given(mockCall).execute()
        willReturn(isResponseSuccessful).given(mockResponse).isSuccessful

        willReturn("testFile").given(mockFile).name
    }
}