package uk.co.aaronvaz.jenkins.plugin;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import hudson.FilePath.FileCallable;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.github.GitHubPlugin;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.github.GHRelease;

import java.io.File;
import java.io.IOException;

import static org.jenkinsci.plugins.github.config.GitHubServerConfig.tokenFor;
import static org.jenkinsci.plugins.github.config.GitHubServerConfig.withHost;
import static org.jenkinsci.plugins.github.util.FluentIterableWrapper.from;

public class GitHubAssetUploadCallable implements FileCallable<Void>
{
    private static final MediaType ARCHIVE_TYPE = MediaType.parse("application/zip");

    private final GHRelease release;

    private final Run<?, ?> run;

    private final TaskListener listener;

    public GitHubAssetUploadCallable(final GHRelease release, final Run<?, ?> run, final TaskListener listener)
    {
        this.release = release;
        this.run = run;
        this.listener = listener;
    }

    @Override
    public Void invoke(final File f, final VirtualChannel channel)
        throws IOException, InterruptedException
    {
        final String apiURL = release.getRoot().getApiUrl();
        final OkHttpClient httpClient = getHttpClient(apiURL);
        final Request request = buildAssetUploadRequest(release.getUploadUrl(), f, apiURL);
        final Response response = httpClient.newCall(request).execute();

        if(!response.isSuccessful())
        {
            listener.error("Error uploading artifacts response code returned: %d \n", response.code());
            listener.error("Response body: %s \n", response.body().string());
            run.setResult(Result.FAILURE);
        }
        return null;
    }

    @Override
    public void checkRoles(final RoleChecker checker)
        throws SecurityException
    {

    }

    private OkHttpClient getHttpClient(final String apiURL)
    {
        final Jenkins jenkins = Jenkins.getInstance();
        if(jenkins.proxy == null)
        {
            return new OkHttpClient();
        }
        else
        {
            return new OkHttpClient().setProxy(jenkins.proxy.createProxy(apiURL));
        }
    }

    private Request buildAssetUploadRequest(final String uploadURL, final File artifact, final String apiURL)
    {
        return new Request.Builder()
            .url(createUploadURL(uploadURL, artifact.getName()))
            .post(RequestBody.create(ARCHIVE_TYPE, artifact))
            .addHeader("User-Agent", "Jenkins/" + Jenkins.getVersion().toString())
            .addHeader("Authorization", "token " + getApiToken(apiURL))
            .addHeader("Content-Type", ARCHIVE_TYPE.toString())
            .build();
    }

    private String createUploadURL(final String urlTemplate, final String artifactName)
    {
        return urlTemplate.replace("{?name,label}", "?name=" + artifactName);
    }

    private String getApiToken(final String apiURL)
    {
        final GitHubServerConfig gitHubServerConfig = from(GitHubPlugin.configuration().getConfigs())
            .filter(withHost(apiURL))
            .first()
            .orNull();
        return tokenFor(gitHubServerConfig.getCredentialsId());
    }
}
