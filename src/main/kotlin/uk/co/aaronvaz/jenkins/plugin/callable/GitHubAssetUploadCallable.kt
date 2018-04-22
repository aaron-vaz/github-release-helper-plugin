package uk.co.aaronvaz.jenkins.plugin.callable

import com.squareup.okhttp.MediaType
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.RequestBody
import hudson.FilePath.FileCallable
import hudson.model.Result
import hudson.model.Run
import hudson.model.TaskListener
import hudson.remoting.VirtualChannel
import jenkins.model.Jenkins
import org.jenkinsci.plugins.github.GitHubPlugin
import org.jenkinsci.plugins.github.config.GitHubServerConfig
import org.jenkinsci.remoting.RoleChecker
import org.kohsuke.github.GHRelease
import java.io.File
import java.io.IOException

class GitHubAssetUploadCallable(var listener: TaskListener,
                                var run: Run<*, *>,
                                var release: GHRelease,
                                var client: OkHttpClient) : FileCallable<Unit>
{
    private val archiveType: MediaType = MediaType.parse("application/zip")

    @Throws(IOException::class, InterruptedException::class)
    override fun invoke(f: File, channel: VirtualChannel)
    {
        val apiURL = release.root.apiUrl
        val httpClient = setupProxy(client, apiURL)
        val request = buildAssetUploadRequest(release.uploadUrl, f, apiURL)
        val response = httpClient.newCall(request).execute()

        if(!response.isSuccessful)
        {
            with(listener)
            {
                error("Error uploading artifacts response code returned: ${response.code()} \n")
                error("Response body: ${response.body().string()} \n")
                error("Deleting release ${release.name} \n")
            }
            release.delete()
            run.setResult(Result.FAILURE)
        }
    }

    private fun setupProxy(client: OkHttpClient, apiURL: String): OkHttpClient
    {
        val jenkins = getJenkinsInstance()
        if(jenkins.proxy != null)
        {
            client.proxy = jenkins.proxy.createProxy(apiURL)
        }
        return client
    }

    private fun buildAssetUploadRequest(uploadURL: String, artifact: File, apiURL: String): Request
    {
        return Request.Builder()
            .url(createUploadURL(uploadURL, artifact.name))
            .post(RequestBody.create(archiveType, artifact))
            .addHeader("User-Agent", "Jenkins/${Jenkins.getVersion()}")
            .addHeader("Authorization", "token ${getApiToken(apiURL)}")
            .addHeader("Content-Type", archiveType.toString())
            .build()
    }

    private fun createUploadURL(urlTemplate: String, artifactName: String): String
    {
        return urlTemplate.replace("{?name,label}", "?name=$artifactName")
    }

    internal fun getApiToken(apiURL: String): String
    {
        val gitHubServerConfig = GitHubPlugin.configuration().configs.first { it.apiUrl == apiURL }
        return GitHubServerConfig.tokenFor(gitHubServerConfig.credentialsId)
    }

    internal fun getJenkinsInstance(): Jenkins
    {
        return Jenkins.getInstance()
    }

    @Throws(SecurityException::class)
    override fun checkRoles(checker: RoleChecker)
    {
    }
}
