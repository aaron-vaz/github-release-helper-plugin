package uk.co.aaronvaz.jenkins.plugin

import com.cloudbees.jenkins.GitHubRepositoryName
import com.cloudbees.jenkins.GitHubRepositoryNameContributor
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.spy
import com.nhaarman.mockito_kotlin.verify
import hudson.Launcher
import hudson.model.AbstractBuild
import hudson.model.BuildListener
import hudson.model.FreeStyleProject
import hudson.model.Item
import hudson.model.Result
import jenkins.model.Jenkins
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.TestBuilder
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GHReleaseBuilder
import org.kohsuke.github.GHRepository
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.willReturn
import org.mockito.BDDMockito.willThrow
import uk.co.aaronvaz.jenkins.plugin.callable.GitHubAssetUploadCallable
import uk.co.aaronvaz.jenkins.plugin.callable.GitHubAssetUploadCallableFactory
import java.io.IOException

class GithubReleaseCreatorTest
{
    @get:Rule
    @Suppress("MemberVisibilityCanBePrivate")
    val jenkinsRule = JenkinsRule()

    private lateinit var jenkins: Jenkins

    private lateinit var project: FreeStyleProject

    private val mockGitHubRepositoryNameContributor = MockGitHubRepositoryNameContributor()

    private val mockGitHubRepositoryName = mockGitHubRepositoryNameContributor.mockGitHubRepositoryName

    private val mockGHRepository: GHRepository = mock()

    private val mockGithubReleaseBuilder: GHReleaseBuilder = spy(GHReleaseBuilder(mockGHRepository, "v1.o"))

    private val mockGithubRelease: GHRelease = mock()

    private val mockGitHubAssetUploadCallableFactory: GitHubAssetUploadCallableFactory = mock()

    private val mockGitHubAssetUploadCallable: GitHubAssetUploadCallable = mock()

    @Before
    @Throws(Exception::class)
    fun setUp()
    {
        jenkins = jenkinsRule.jenkins
        project = jenkinsRule.createFreeStyleProject()
        project.buildersList.add(WriteToFileBuilder())

        val githubReleaseCreator = GithubReleaseCreator("https://localhost/test-repo.git",
                                                        "v1.0",
                                                        "master",
                                                        "Test Release",
                                                        "Release Message",
                                                        false,
                                                        false,
                                                        "**/*.txt")

        githubReleaseCreator.githubCallableFactory = mockGitHubAssetUploadCallableFactory
        willReturn(mockGitHubAssetUploadCallable).given(mockGitHubAssetUploadCallableFactory).build(any(), any(), any(), any())

        project.publishersList.add(githubReleaseCreator)

        // increase coverage
        githubReleaseCreator.githubCallableFactory

        val descriptor = GithubReleaseCreator.DescriptorImpl()
        descriptor.displayName
        descriptor.isApplicable(project.javaClass)
    }

    @Test
    @Throws(Exception::class)
    fun perform_NoGithubReposDefined_ExceptionThrown()
    {
        // when
        val freeStyleBuild = project.scheduleBuild2(0).get()

        // then
        jenkinsRule.assertBuildStatus(Result.FAILURE, freeStyleBuild)
        jenkinsRule.assertLogContains("No Github repos found with URL: https://localhost/test-repo.git", freeStyleBuild)
    }

    @Test
    fun perform_JenkinsInstanceSetupCorrectly_SuccessfulBuild()
    {
        // given
        setupCommonMocks()
        willReturn(mockGithubRelease).given(mockGithubReleaseBuilder).create()

        // when
        val freeStyleBuild = project.scheduleBuild2(0).get()

        // then
        jenkinsRule.assertBuildStatusSuccess(freeStyleBuild)
        jenkinsRule.assertLogContains("Creating Github release using commit master", freeStyleBuild)
        verify(mockGitHubAssetUploadCallable).invoke(any(), any())
    }

    @Test
    fun perform_ErrorCreatingRelease_FailedBuild()
    {
        // given
        setupCommonMocks()
        willThrow(IOException("Release already exists")).given(mockGithubReleaseBuilder).create()

        // when
        val freeStyleBuild = project.scheduleBuild2(0).get()

        // then
        jenkinsRule.assertBuildStatus(Result.FAILURE, freeStyleBuild)
    }

    private fun setupCommonMocks()
    {
        jenkins.getExtensionList(GitHubRepositoryNameContributor::class.java).add(0, mockGitHubRepositoryNameContributor)
        willReturn(listOf(mockGHRepository)).given(mockGitHubRepositoryName).resolve()
        willReturn("https://localhost/test-repo.git").given(mockGHRepository).gitHttpTransportUrl()
        willReturn(mockGithubReleaseBuilder).given(mockGHRepository).createRelease(eq("v1.0"))
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
