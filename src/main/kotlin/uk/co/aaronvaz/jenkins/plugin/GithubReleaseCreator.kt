package uk.co.aaronvaz.jenkins.plugin

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
import java.io.IOException

class GithubReleaseCreator @DataBoundConstructor
constructor(@JvmField val repoURL: String,
    @JvmField val releaseTag: String,
    @JvmField val commitish: String,
    @JvmField val releaseName: String,
    @JvmField val releaseBody: String,
    @JvmField val isPrerelease: Boolean,
    @JvmField val isDraftRelease: Boolean,
    @JvmField val artifactPatterns: String) : Notifier(), SimpleBuildStep
{
    var githubCallable: GitHubAssetUploadCallable = GitHubAssetUploadCallable()

    @Throws(InterruptedException::class, IOException::class)
    override fun perform(run: Run<*, *>, workspace: FilePath, launcher: Launcher, listener: TaskListener)
    {
        val repo = getGHRepository(repoURL) ?: throw RuntimeException("No Github repos found with URL: " + repoURL)

        listener.logger.println("Creating Github release using commit " + commitish)
        val createdRelease = createRelease(repo, run, listener)

        for(artifactPath in workspace.list(artifactPatterns))
        {
            listener.logger.println("Uploading artifact " + artifactPath.name)
            artifactPath.act(githubCallable.apply {
                this.listener = listener
                this.run = run
                this.release = createdRelease
            })
        }
    }

    private fun getGHRepository(repoURL: String): GHRepository?
    {
        val items = Jenkins.getInstance().getAllItems<Item>(Item::class.java)
        val repos = from(items).transformAndConcat(associatedNames()).toList()

        return repos
            .flatMap { it.resolve() }
            .firstOrNull { repoURL.equals(it.gitHttpTransportUrl(), ignoreCase = true) || repoURL.equals(it.sshUrl, ignoreCase = true) }
    }

    private fun createRelease(repo: GHRepository, run: Run<*, *>, listener: TaskListener): GHRelease?
    {
        try
        {
            val releaseBuilder = repo.createRelease(releaseTag)
            releaseBuilder.body(releaseBody)
            releaseBuilder.commitish(commitish)
            releaseBuilder.name(releaseName)
            releaseBuilder.draft(isDraftRelease)
            releaseBuilder.prerelease(isPrerelease)
            return releaseBuilder.create()
        }
        catch(e: IOException)
        {
            listener.error("Error creating GitHub release")
            e.printStackTrace(listener.logger)
            run.setResult(Result.FAILURE)
        }

        return null
    }

    override fun getRequiredMonitorService(): BuildStepMonitor
    {
        return BuildStepMonitor.NONE
    }

    @Symbol("githubRelease")
    @Extension
    open class DescriptorImpl : BuildStepDescriptor<Publisher>()
    {
        init
        {
            load()
        }

        override fun getDisplayName(): String
        {
            return "Create a Release Using the SCM service's API"
        }

        override fun isApplicable(jobType: Class<out AbstractProject<*, *>>): Boolean
        {
            return true
        }
    }
}
