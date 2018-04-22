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
import org.jenkinsci.plugins.github.util.FluentIterableWrapper.from
import org.jenkinsci.plugins.github.util.JobInfoHelpers.associatedNames
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GHRepository
import org.kohsuke.stapler.DataBoundConstructor
import uk.co.aaronvaz.jenkins.plugin.callable.GitHubAssetUploadCallableFactory

class GithubReleaseCreator
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
    var githubCallableFactory = GitHubAssetUploadCallableFactory()

    override fun perform(run: Run<*, *>, workspace: FilePath, launcher: Launcher, listener: TaskListener)
    {
        try
        {
            val repo = getGHRepository(repoURL) ?: throw RuntimeException("No Github repos found with URL: $repoURL")

            listener.logger.println("Creating Github release using commit $commitish")
            val release = createRelease(repo)

            for(artifactPath in workspace.list(artifactPatterns))
            {
                listener.logger.println("Uploading artifact ${artifactPath.name}")
                artifactPath.act(githubCallableFactory.build(listener, run, release, OkHttpClient()))
            }
        }
        catch(e: Exception)
        {
            listener.error("An error occurred while creating this release")
            e.printStackTrace(listener.logger)
            run.setResult(Result.FAILURE)
        }
    }

    private fun getGHRepository(repoURL: String): GHRepository?
    {
        val items = Jenkins.getInstance().getAllItems<Item>(Item::class.java)
        val repos = from(items).transformAndConcat(associatedNames())

        return repos.flatMap { it.resolve() }
            .firstOrNull { repoURL.equals(it.gitHttpTransportUrl(), ignoreCase = true) || repoURL.equals(it.sshUrl, ignoreCase = true) }
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
            return "Create a Release Using the Github API"
        }

        override fun isApplicable(jobType: Class<out AbstractProject<*, *>>): Boolean
        {
            return true
        }
    }
}
