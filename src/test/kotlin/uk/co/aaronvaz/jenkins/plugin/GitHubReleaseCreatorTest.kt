package uk.co.aaronvaz.jenkins.plugin

import com.cloudbees.jenkins.GitHubRepositoryName
import com.cloudbees.jenkins.GitHubRepositoryNameContributor
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.spy
import com.squareup.okhttp.Call
import com.squareup.okhttp.MediaType
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Response
import com.squareup.okhttp.ResponseBody
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

    private val mockOkHttpClient: OkHttpClient = mock()

    private val mockCall: Call = mock()

    private val mockResponse: Response = mock()

    private val testRepoURL = "https://localhost/test-repo.git"

    private val testApiURL = "http://localhost/api/v3"

    @Before
    @Throws(Exception::class)
    fun setUp()
    {
        jenkins = jenkinsRule.jenkins
        project = jenkinsRule.createFreeStyleProject()
        project.buildersList.add(WriteToFileBuilder())

        val gitHubTokenCredentialsCreator = GitHubTokenCredentialsCreator()
        val credentials = gitHubTokenCredentialsCreator.createCredentials(testApiURL, "token", "testUser")

        val gitHubServerConfig = GitHubServerConfig(credentials.id)
        gitHubServerConfig.apiUrl = testApiURL
        GitHubPlugin.configuration().configs = listOf(gitHubServerConfig)

        HttpClientFactory.clients[null] = mockOkHttpClient

        val githubReleaseCreator = GitHubReleaseCreator(testRepoURL,
                                                        "v1.0",
                                                        "master",
                                                        "Test Release",
                                                        "Release Message",
                                                        false,
                                                        false,
                                                        "**/*.txt")


        project.publishersList.add(githubReleaseCreator)

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
        setupHappyPath()
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
        setupHappyPath()
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
        setupHappyPath()
        willReturn(mockGitHubRelease).given(mockGitHubReleaseBuilder).create()
        willReturn(false).given(mockResponse).isSuccessful
        willReturn(500).given(mockResponse).code()

        // when
        val freeStyleBuild = project.scheduleBuild2(0).get()

        // then
        jenkinsRule.assertBuildStatus(Result.FAILURE, freeStyleBuild)
        jenkinsRule.assertLogContains("Error uploading artifacts response code returned: 500", freeStyleBuild)
        jenkinsRule.assertLogContains("Response body: response message", freeStyleBuild)
    }

    @Test
    fun perform_JenkinsInstanceSetupCorrectly_SuccessfulBuild()
    {
        // given
        setupHappyPath()
        willReturn(mockGitHubRelease).given(mockGitHubReleaseBuilder).create()

        // when
        val freeStyleBuild = project.scheduleBuild2(0).get()

        // then
        jenkinsRule.assertBuildStatusSuccess(freeStyleBuild)
        jenkinsRule.assertLogContains("Creating GitHub release v1.0 using commit master", freeStyleBuild)
    }

    private fun setupHappyPath()
    {
        jenkins.getExtensionList(GitHubRepositoryNameContributor::class.java).add(0, mockGitHubRepositoryNameContributor)

        willReturn(listOf(mockGHRepository)).given(mockGitHubRepositoryName).resolve()
        willReturn(testRepoURL).given(mockGHRepository).gitHttpTransportUrl()

        willReturn(mockGitHubReleaseBuilder).given(mockGHRepository).createRelease(eq("v1.0"))
        willReturn(mockPagedIterable).given(mockGHRepository).listReleases()
        willReturn(listOf(mockGitHubRelease)).given(mockPagedIterable).asList()

        willReturn(mockGithubRoot).given(mockGitHubRelease).root
        willReturn("http://upload.localhost/").given(mockGitHubRelease).uploadUrl

        willReturn(testApiURL).given(mockGithubRoot).apiUrl

        willReturn(mockCall).given(mockOkHttpClient).newCall(any())
        willReturn(mockResponse).given(mockCall).execute()
        willReturn(true).given(mockResponse).isSuccessful
        willReturn(201).given(mockResponse).code()

        val responseBody = ResponseBody.create(MediaType.parse("application/text"), "response message")
        willReturn(responseBody).given(mockResponse).body()
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
