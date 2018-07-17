package uk.co.aaronvaz.jenkins.plugin.callable

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.squareup.okhttp.Call
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Response
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.remoting.VirtualChannel
import org.junit.Test
import org.kohsuke.github.GHAsset
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GitHub
import org.mockito.BDDMockito.willReturn
import java.io.File
import java.io.PrintStream

class GitHubAssetUploadCallableTest
{
    private val mockListener: TaskListener = mock()

    private val mockRun: Run<*, *> = mock()

    private val mockRoot: GitHub = mock()

    private val mockRelease: GHRelease = mock()

    private val mockAsset: GHAsset = mock()

    private val mockClient: OkHttpClient = mock()

    private val mockCall: Call = mock()

    private val mockResponse: Response = mock()

    private val mockFile: File = mock()

    private val mockVirtualChannel: VirtualChannel = mock()

    private val testApiURL = "https://test.github.com/api/v3"

    private val testUploadURL = "https://test.github.com/uploads/api/{?name,label}"

    private val gitHubAssetUploadCallable = GitHubAssetUploadCallable(mockListener, mockRun, mockRelease, "", mockClient)

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
            verify(mockClient).newCall(any())
            verify(mockCall).execute()

            verify(mockListener, never()).error(any())
            verify(mockRun, never()).setResult(Result.FAILURE)
        }
    }

    @Test
    fun invoke_AssetSameSizeAsRemote_AssetNotUploaded()
    {
        // given
        setupMocks(true)
        willReturn(listOf(mockAsset)).given(mockRelease).assets
        willReturn("testFile").given(mockAsset).name
        willReturn(10L).given(mockAsset).size

        // when
        gitHubAssetUploadCallable.invoke(mockFile, mockVirtualChannel)

        // then
        inOrder(mockAsset, mockClient, mockCall, mockListener, mockRun)
        {
            verify(mockAsset, never()).delete()
            verify(mockClient, never()).newCall(any())
            verify(mockCall, never()).execute()

            verify(mockListener, never()).error(any())
            verify(mockRun, never()).setResult(Result.FAILURE)
        }
    }

    @Test
    fun invoke_AssetAlreadyExists_AssetDeletedBeforeNewUploaded()
    {
        // given
        setupMocks(true)
        willReturn(listOf(mockAsset)).given(mockRelease).assets
        willReturn("testFile").given(mockAsset).name
        willReturn(20L).given(mockAsset).size

        // when
        gitHubAssetUploadCallable.invoke(mockFile, mockVirtualChannel)

        // then
        inOrder(mockAsset, mockClient, mockCall, mockListener, mockRun)
        {
            verify(mockAsset).delete()
            verify(mockClient).newCall(any())
            verify(mockCall).execute()

            verify(mockListener, never()).error(any())
            verify(mockRun, never()).setResult(Result.FAILURE)
        }
    }

    @Test
    fun invoke_ApiRequestFailed_AssetsNotUploadedAndBuildFailed()
    {
        // given
        setupMocks(false)

        // when
        gitHubAssetUploadCallable.invoke(mockFile, mockVirtualChannel)

        // then
        inOrder(mockClient, mockCall, mockListener, mockRun)
        {
            verify(mockClient).newCall(any())
            verify(mockCall).execute()

            verify(mockListener, times(2)).error(any())
            verify(mockRun).setResult(Result.FAILURE)
        }
    }

    private fun setupMocks(isResponseSuccessful: Boolean)
    {
        willReturn(mock<PrintStream>()).given(mockListener).logger

        willReturn(testUploadURL).given(mockRelease).uploadUrl
        willReturn(mockRoot).given(mockRelease).root
        willReturn(testApiURL).given(mockRoot).apiUrl

        willReturn(mockCall).given(mockClient).newCall(any())
        willReturn(mockResponse).given(mockCall).execute()
        willReturn(isResponseSuccessful).given(mockResponse).isSuccessful

        willReturn("testFile").given(mockFile).name
        willReturn(10L).given(mockFile).length()
    }
}