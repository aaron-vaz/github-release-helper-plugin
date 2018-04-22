package uk.co.aaronvaz.jenkins.plugin.callable

import com.squareup.okhttp.OkHttpClient
import hudson.model.Run
import hudson.model.TaskListener
import org.kohsuke.github.GHRelease

class GitHubAssetUploadCallableFactory
{
    fun build(listener: TaskListener,
              run: Run<*, *>,
              release: GHRelease,
              client: OkHttpClient): GitHubAssetUploadCallable
    {
        return GitHubAssetUploadCallable(listener, run, release, client)
    }
}