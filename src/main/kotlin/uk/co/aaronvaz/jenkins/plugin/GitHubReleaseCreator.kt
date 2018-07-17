package uk.co.aaronvaz.jenkins.plugin

import com.squareup.okhttp.OkHttpClient
import hudson.Extension
import hudson.FilePath
import hudson.Launcher
import hudson.model.AbstractProject
import hudson.model.Item
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.tasks.BuildStepDescriptor
import hudson.tasks.BuildStepMonitor
import hudson.tasks.Notifier
import hudson.tasks.Publisher
import jenkins.model.Jenkins
import jenkins.tasks.SimpleBuildStep
import org.jenkinsci.Symbol
import org.jenkinsci.plugins.github.GitHubPlugin
import org.jenkinsci.plugins.github.config.GitHubServerConfig
import org.jenkinsci.plugins.github.util.FluentIterableWrapper.from
import org.jenkinsci.plugins.github.util.JobInfoHelpers.associatedNames
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GHRepository
import org.kohsuke.stapler.DataBoundConstructor
import uk.co.aaronvaz.jenkins.plugin.callable.GitHubAssetUploadCallable
import uk.co.aaronvaz.jenkins.plugin.http.HttpClientFactory
import java.net.URL

class GitHubReleaseCreator
@DataBoundConstructor
constructor(private val repoURL: String,
            private val releaseTag: String,
            private val commitish: String,
            private val releaseName: String,
            private val releaseBody: String,
            private val isPreRelease: Boolean,
            private val isDraftRelease: Boolean,
            private val artifactPatterns: String) : Notifier(), SimpleBuildStep
{
    private val jenkins = Jenkins.getInstance()

    override fun perform(run: Run<*, *>, workspace: FilePath, launcher: Launcher, listener: TaskListener)
    {
        try
        {
            val repo = getGHRepository(repoURL) ?: throw RuntimeException("No GitHub repos found with URL: $repoURL")
            val release = createOrGetRelease(repo, listener)
            val apiURL = release.root.apiUrl
            val apiToken = getApiToken(apiURL)

            if(artifactPatterns.isNotBlank())
            {
                for(artifactPath in workspace.list(artifactPatterns))
                {
                    artifactPath.act(GitHubAssetUploadCallable(listener, run, release, apiToken, getHttpClient(apiURL)))
                }
            }
            else
            {
                listener.logger.println("Artifacts pattern not supplied, skipping artifact upload")
            }
        }
        catch(e: Exception)
        {
            listener.error("An error occurred while creating this release")
            e.printStackTrace(listener.logger)
            run.setResult(Result.FAILURE)
        }
    }

    private fun getApiToken(apiURL: String): String
    {
        val gitHubServerConfig = GitHubPlugin.configuration().configs.first { URL(it.apiUrl).host == URL(apiURL).host }
        return GitHubServerConfig.tokenFor(gitHubServerConfig.credentialsId)
    }

    private fun getGHRepository(repoURL: String): GHRepository?
    {
        val items = jenkins.getAllItems<Item>(Item::class.java)
        val repos = from(items).transformAndConcat(associatedNames())

        return repos.flatMap { it.resolve() }
            .firstOrNull { repoURL.equals(it.gitHttpTransportUrl(), true) || repoURL.equals(it.sshUrl, true) }
    }

    private fun createOrGetRelease(repo: GHRepository, listener: TaskListener): GHRelease
    {
        val release = repo.listReleases().asList().firstOrNull { releaseTag == it.tagName }
        if(release != null)
        {
            listener.logger.println("Release $releaseTag already exists continuing")
            return release
        }
        listener.logger.println("Creating GitHub release $releaseTag using commit $commitish")
        return createRelease(repo)
    }

    private fun createRelease(repo: GHRepository): GHRelease
    {
        return repo.createRelease(releaseTag)
            .body(releaseBody)
            .commitish(commitish)
            .name(releaseName)
            .draft(isDraftRelease)
            .prerelease(isPreRelease)
            .create()
    }

    private fun getHttpClient(apiURL: String): OkHttpClient
    {
        val proxy = jenkins.proxy?.createProxy(apiURL)
        return HttpClientFactory.buildHttpClient(proxy)!!
    }

    override fun getRequiredMonitorService(): BuildStepMonitor
    {
        return BuildStepMonitor.NONE
    }

    @Symbol("githubRelease")
    @Extension
    class DescriptorImpl : BuildStepDescriptor<Publisher>()
    {
        init
        {
            load()
        }

        override fun getDisplayName(): String
        {
            return "Create a Release Using the GitHub API"
        }

        override fun isApplicable(jobType: Class<out AbstractProject<*, *>>): Boolean
        {
            return true
        }
    }
}
