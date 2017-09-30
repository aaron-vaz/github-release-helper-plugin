package uk.co.aaronvaz.jenkins.plugin;

import com.cloudbees.jenkins.GitHubRepositoryName;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHReleaseBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

import static org.jenkinsci.plugins.github.util.FluentIterableWrapper.from;
import static org.jenkinsci.plugins.github.util.JobInfoHelpers.associatedNames;

public class GithubReleaseCreator extends Notifier implements SimpleBuildStep
{
    private final String repoURL;

    private final String releaseTag;

    private final String commitish;

    private final String releaseName;

    private final String releaseBody;

    private final boolean isPrerelease;

    private final boolean isDraftRelease;

    private final String artifactPatterns;

    @DataBoundConstructor
    public GithubReleaseCreator(final String repoURL,
        final String releaseTag,
        final String commitish,
        final String releaseName,
        final String releaseBody,
        final boolean isPrerelease,
        final boolean isDraftRelease,
        final String artifactPatterns)
    {

        this.repoURL = repoURL;
        this.releaseTag = releaseTag;
        this.commitish = commitish;
        this.releaseName = releaseName;
        this.releaseBody = releaseBody;
        this.isPrerelease = isPrerelease;
        this.isDraftRelease = isDraftRelease;
        this.artifactPatterns = artifactPatterns;
    }

    @Override
    public void perform(@Nonnull final Run<?, ?> run, @Nonnull final FilePath workspace, @Nonnull final Launcher launcher, @Nonnull final TaskListener listener)
        throws InterruptedException, IOException
    {
        final GHRepository repo = getGHRepository(repoURL);

        if(repo == null)
        {
            throw new RuntimeException("No Github repos found with URL: " + repoURL);
        }

        listener.getLogger().println("Creating Github release using commit " + commitish);
        final GHRelease createdRelease = createRelease(repo, run, listener);

        for(final FilePath artifactPath : workspace.list(artifactPatterns))
        {
            listener.getLogger().println("Uploading artifact " + artifactPath.getName());
            artifactPath.act(new GitHubAssetUploadCallable(createdRelease, run, listener));
        }
    }

    private GHRepository getGHRepository(final String repoURL)
    {
        final List<Item> items = Jenkins.getInstance().getAllItems(Item.class);
        final List<GitHubRepositoryName> repos = from(items).transformAndConcat(associatedNames()).toList();

        for(GitHubRepositoryName name : repos)
        {
            for(GHRepository repository : name.resolve())
            {
                if(repoURL.equalsIgnoreCase(repository.gitHttpTransportUrl())
                    || repoURL.equalsIgnoreCase(repository.getSshUrl()))
                {
                    return repository;
                }
            }
        }
        return null;
    }

    private GHRelease createRelease(final GHRepository repo, final @Nonnull Run<?, ?> run, final TaskListener listener)
    {
        try
        {
            final GHReleaseBuilder releaseBuilder = repo.createRelease(releaseTag);
            releaseBuilder.body(releaseBody);
            releaseBuilder.commitish(commitish);
            releaseBuilder.name(releaseName);
            releaseBuilder.draft(isDraftRelease);
            releaseBuilder.prerelease(isPrerelease);
            return releaseBuilder.create();
        }
        catch(final IOException e)
        {
            listener.error("Error creating GitHub release");
            e.printStackTrace(listener.getLogger());
            run.setResult(Result.FAILURE);
        }
        return null;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService()
    {
        return BuildStepMonitor.NONE;
    }

    @Symbol("githubRelease")
    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher>
    {
        public DescriptorImpl()
        {
            super();
            load();
        }

        @Nonnull
        @Override
        public String getDisplayName()
        {
            return "Create a Release Using the SCM service's API";
        }

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType)
        {
            return true;
        }
    }
}
