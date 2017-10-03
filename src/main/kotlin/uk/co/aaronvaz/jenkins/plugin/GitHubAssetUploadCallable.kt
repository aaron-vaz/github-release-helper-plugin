package uk.co.aaronvaz.jenkins.plugin

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

class GitHubAssetUploadCallable(private val release: GHRelease, private val run: Run<*, *>, private val listener: TaskListener) : FileCallable<Void>
{
    private val archiveType = MediaType.parse("application/zip")

    @Throws(IOException::class, InterruptedException::class)
    override fun invoke(f: File, channel: VirtualChannel): Void?
    {
        val apiURL = release.root.apiUrl
        val httpClient = getHttpClient(apiURL)
        val request = buildAssetUploadRequest(release.uploadUrl, f, apiURL)
        val response = httpClient.newCall(request).execute()

        if(!response.isSuccessful)
        {
            with(listener) {
                error("Error uploading artifacts response code returned: %d \n", response.code())
                error("Response body: %s \n", response.body().string())
            }
            run.setResult(Result.FAILURE)
        }
        return null
    }

    @Throws(SecurityException::class)
    override fun checkRoles(checker: RoleChecker)
    {

    }

    private fun getHttpClient(apiURL: String): OkHttpClient
    {
        val jenkins = Jenkins.getInstance()
        return if(jenkins.proxy == null)
        {
            OkHttpClient()
        }
        else
        {
            OkHttpClient().setProxy(jenkins.proxy.createProxy(apiURL))
        }
    }

    private fun buildAssetUploadRequest(uploadURL: String, artifact: File, apiURL: String): Request
    {
        return Request.Builder()
            .url(createUploadURL(uploadURL, artifact.name))
            .post(RequestBody.create(archiveType, artifact))
            .addHeader("User-Agent", "Jenkins/" + Jenkins.getVersion()!!.toString())
            .addHeader("Authorization", "token " + getApiToken(apiURL))
            .addHeader("Content-Type", archiveType.toString())
            .build()
    }

    private fun createUploadURL(urlTemplate: String, artifactName: String): String
    {
        return urlTemplate.replace("{?name,label}", "?name=" + artifactName)
    }

    private fun getApiToken(apiURL: String): String
    {
        val gitHubServerConfig = GitHubPlugin.configuration().configs.first { gitHubServerConfig ->
            gitHubServerConfig.apiUrl == apiURL
        }
        return GitHubServerConfig.tokenFor(gitHubServerConfig.credentialsId)
    }
}
