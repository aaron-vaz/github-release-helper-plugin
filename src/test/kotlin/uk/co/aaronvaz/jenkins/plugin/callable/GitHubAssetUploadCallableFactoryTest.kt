package uk.co.aaronvaz.jenkins.plugin.callable

import com.nhaarman.mockito_kotlin.mock
import com.squareup.okhttp.OkHttpClient
import hudson.model.Run
import hudson.model.TaskListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.kohsuke.github.GHRelease

class GitHubAssetUploadCallableFactoryTest
{
    private val mockListener: TaskListener = mock()

    private val mockRun: Run<*, *> = mock()

    private val mockRelease: GHRelease = mock()

    private val mockClient: OkHttpClient = mock()

    private val gitHubAssetUploadCallableFactory = GitHubAssetUploadCallableFactory()

    @Test
    fun build_ValidParameters_CallableBuilt()
    {
        val callable = gitHubAssetUploadCallableFactory.build(mockListener, mockRun, mockRelease, mockClient)

        assertThat(callable.listener).isEqualTo(mockListener)
        assertThat(callable.run).isEqualTo(mockRun)
        assertThat(callable.release).isEqualTo(mockRelease)
        assertThat(callable.client).isEqualTo(mockClient)
    }
}