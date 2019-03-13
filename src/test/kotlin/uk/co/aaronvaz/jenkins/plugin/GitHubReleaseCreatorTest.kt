package uk.co.aaronvaz.jenkins.plugin

import com.cloudbees.jenkins.GitHubRepositoryName
import com.cloudbees.jenkins.GitHubRepositoryNameContributor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.squareup.okhttp.mockwebserver.MockResponse
import com.squareup.okhttp.mockwebserver.MockWebServer
import hudson.Launcher
import hudson.model.AbstractBuild
import hudson.model.BuildListener
import hudson.model.FreeStyleProject
import hudson.model.Item
import hudson.model.Result
import jenkins.model.Jenkins
import org.jenkinsci.plugins.github.GitHubPlugin
import org.jenkinsci.plugins.github.config.GitHubServerConfig
import org.jenkinsci.plugins.github.config.GitHubTokenCredentialsCreator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.TestBuilder
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GHReleaseBuilder
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.github.PagedIterable
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.willReturn
import org.mockito.BDDMockito.willThrow
import uk.co.aaronvaz.jenkins.plugin.http.HttpClientFactory
import java.io.IOException
import java.net.URLDecoder

class GitHubReleaseCreatorTest
{
    @get:Rule
    @Suppress("MemberVisibilityCanBePrivate")
    val jenkinsRule = JenkinsRule()

    private lateinit var jenkins: Jenkins

    private lateinit var project: FreeStyleProject

    private val mockGitHubRepositoryNameContributor = MockGitHubRepositoryNameContributor()

    private val mockGitHubRepositoryName = mockGitHubRepositoryNameContributor.mockGitHubRepositoryName

    private val mockGHRepository: GHRepository = mock()

    private val mockPagedIterable: PagedIterable<GHRelease> = mock()

    private val mockGitHubReleaseBuilder: GHReleaseBuilder = spy(GHReleaseBuilder(mockGHRepository, "v1.o"))

    private val mockGitHubRelease: GHRelease = mock()

    private val mockGithubRoot: GitHub = mock()

    private val testRepoURL = "https://localhost/test-repo.git"

    private val testApiURL = "http://localhost/api/v3"

    private lateinit var mockServer: MockWebServer

    private lateinit var testUploadURL: String

    @Before
    @Throws(Exception::class)
    fun setUp()
    {
        jenkins = jenkinsRule.jenkins
        project = jenkinsRule.createFreeStyleProject()
        project.buildersList.add(WriteToFileBuilder())

        mockServer = MockWebServer()
        mockServer.start()

        testUploadURL = URLDecoder.decode(mockServer.url("/upload{?name,label}").toString(), Charsets.UTF_8.name())

        val gitHubTokenCredentialsCreator = GitHubTokenCredentialsCreator()
        val credentials = gitHubTokenCredentialsCreator.createCredentials(testApiURL, "token", "testUser")

        val gitHubServerConfig = GitHubServerConfig(credentials.id)
        gitHubServerConfig.apiUrl = testApiURL
        GitHubPlugin.configuration().configs = listOf(gitHubServerConfig)

        // increase coverage
        val descriptor = GitHubReleaseCreator.DescriptorImpl()
        descriptor.displayName
        descriptor.isApplicable(project.javaClass)
    }

    @After
    fun tearDown()
    {
        HttpClientFactory.clients.clear()
    }

    @Test
    fun perform_NoGitHubReposDefined_ExceptionThrown()
    {
        // given
        addPlugin("**/*.txt")
        jenkins.getExtensionList(GitHubRepositoryNameContributor::class.java).add(0, mockGitHubRepositoryNameContributor)

        willReturn(listOf(mockGHRepository)).given(mockGitHubRepositoryName).resolve()
        willReturn("some other url").given(mockGHRepository).gitHttpTransportUrl()

        // when
        val freeStyleBuild = project.scheduleBuild2(0).get()

        // then
        jenkinsRule.assertBuildStatus(Result.FAILURE, freeStyleBuild)
        jenkinsRule.assertLogContains("An error occurred while creating this release", freeStyleBuild)
        jenkinsRule.assertLogContains("No GitHub repos found with URL: https://localhost/test-repo.git", freeStyleBuild)
    }

    @Test
    fun perform_ErrorCreatingRelease_FailedBuild()
    {
        // given
        addPlugin("**/*.txt")

        setupCommonMocks()
        willThrow(IOException("Error creating release")).given(mockGitHubReleaseBuilder).create()

        // when
        val freeStyleBuild = project.scheduleBuild2(0).get()

        // then
        jenkinsRule.assertBuildStatus(Result.FAILURE, freeStyleBuild)
        jenkinsRule.assertLogContains("An error occurred while creating this release", freeStyleBuild)
    }

    @Test
    fun perform_ReleaseAlreadyExists_SuccessfulBuild()
    {
        // given
        addPlugin("**/*.txt")

        setupCommonMocks()
        willReturn("v1.0").given(mockGitHubRelease).tagName

        // when
        val freeStyleBuild = project.scheduleBuild2(0).get()

        // then
        jenkinsRule.assertBuildStatusSuccess(freeStyleBuild)
        jenkinsRule.assertLogContains("Release v1.0 already exists continuing", freeStyleBuild)
    }

    @Test
    fun perform_AssetFailedToUpload_FailedBuild()
    {
        // given
        addPlugin("**/*.txt")

        setupCommonMocks {
            // 2 requests for the retry
            it.enqueue(MockResponse().setResponseCode(500).setBody("response message"))
            it.enqueue(MockResponse().setResponseCode(500).setBody("response message"))
        }

        willReturn(mockGitHubRelease).given(mockGitHubReleaseBuilder).create()

        // when
        val freeStyleBuild = project.scheduleBuild2(0).get()

        // then
        assertEquals(2, mockServer.requestCount)

        jenkinsRule.assertBuildStatus(Result.FAILURE, freeStyleBuild)
        jenkinsRule.assertLogContains("Error uploading test.txt response code returned: 500", freeStyleBuild)
        jenkinsRule.assertLogContains("Retrying upload of test.txt", freeStyleBuild)
        jenkinsRule.assertLogContains("Response body: response message", freeStyleBuild)
    }

    @Test
    fun perform_AssetUploadedOnRetry_SuccessfulBuild()
    {
        // given
        addPlugin("**/*.txt")

        setupCommonMocks {
            it.enqueue(MockResponse().setResponseCode(500).setBody("response message"))
            it.enqueue(MockResponse().setResponseCode(201).setBody("response message"))
        }

        willReturn(mockGitHubRelease).given(mockGitHubReleaseBuilder).create()

        // when
        val freeStyleBuild = project.scheduleBuild2(0).get()

        // then
        assertEquals(2, mockServer.requestCount)

        jenkinsRule.assertBuildStatusSuccess(freeStyleBuild)
        jenkinsRule.assertLogContains("Retrying upload of test.txt", freeStyleBuild)
        jenkinsRule.assertLogContains("Creating GitHub release v1.0 using commit master", freeStyleBuild)
    }

    @Test
    fun perform_BlankArtifactPattern_SuccessfulBuildButNoArtifactsUploaded()
    {
        // given
        addPlugin("")

        setupCommonMocks()
        willReturn(mockGitHubRelease).given(mockGitHubReleaseBuilder).create()

        // when
        val freeStyleBuild = project.scheduleBuild2(0).get()

        // then
        assertEquals(0, mockServer.requestCount)

        jenkinsRule.assertBuildStatusSuccess(freeStyleBuild)
        jenkinsRule.assertLogContains("Creating GitHub release v1.0 using commit master", freeStyleBuild)
        jenkinsRule.assertLogContains("Artifacts pattern not supplied, skipping artifact upload", freeStyleBuild)
    }

    @Test
    fun perform_JenkinsInstanceSetupCorrectly_SuccessfulBuild()
    {
        // given
        addPlugin("**/*.txt")
        setupCommonMocks()
        willReturn(mockGitHubRelease).given(mockGitHubReleaseBuilder).create()

        // when
        val freeStyleBuild = project.scheduleBuild2(0).get()

        // then
        assertEquals(1, mockServer.requestCount)

        jenkinsRule.assertBuildStatusSuccess(freeStyleBuild)
        jenkinsRule.assertLogContains("Creating GitHub release v1.0 using commit master", freeStyleBuild)
    }

    private fun addPlugin(artifactPattern: String)
    {
        val githubReleaseCreator = GitHubReleaseCreator(testRepoURL,
                                                        "v1.0",
                                                        "master",
                                                        "Test Release",
                                                        "Release Message",
                                                        false,
                                                        false,
                                                        artifactPattern)


        project.publishersList.add(githubReleaseCreator)
    }

    private fun setupCommonMocks(responseMocks: (MockWebServer) -> Unit =
                                     { it.enqueue(MockResponse().setBody("response message").setResponseCode(201)) })
    {
        jenkins.getExtensionList(GitHubRepositoryNameContributor::class.java).add(0, mockGitHubRepositoryNameContributor)

        willReturn(listOf(mockGHRepository)).given(mockGitHubRepositoryName).resolve()
        willReturn(testRepoURL).given(mockGHRepository).gitHttpTransportUrl()

        willReturn(mockGitHubReleaseBuilder).given(mockGHRepository).createRelease(eq("v1.0"))
        willReturn(mockPagedIterable).given(mockGHRepository).listReleases()
        willReturn(listOf(mockGitHubRelease)).given(mockPagedIterable).asList()

        willReturn(mockGithubRoot).given(mockGitHubRelease).root
        willReturn(testUploadURL).given(mockGitHubRelease).uploadUrl
        willReturn(testApiURL).given(mockGithubRoot).apiUrl

        responseMocks(mockServer)
    }

    class WriteToFileBuilder : TestBuilder()
    {
        override fun perform(build: AbstractBuild<*, *>?, launcher: Launcher?, listener: BuildListener?): Boolean
        {
            build!!.getWorkspace()!!.child("test.txt").write("running junit tests", "UTF-8")
            return true
        }
    }

    class MockGitHubRepositoryNameContributor : GitHubRepositoryNameContributor()
    {
        val mockGitHubRepositoryName: GitHubRepositoryName = mock()

        override fun parseAssociatedNames(item: Item?, result: MutableCollection<GitHubRepositoryName>?)
        {
            if(result is Set<*>)
            {
                result += mockGitHubRepositoryName
            }
        }
    }
}
