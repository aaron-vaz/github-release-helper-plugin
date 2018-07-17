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
import org.jenkinsci.remoting.RoleChecker
import org.kohsuke.github.GHRelease
import java.io.File
import java.io.IOException

class GitHubAssetUploadCallable(private val listener: TaskListener,
                                private val run: Run<*, *>,
                                private val release: GHRelease,
                                private val apiToken: String,
                                private val client: OkHttpClient) : FileCallable<Unit>
{
    private val archiveType: MediaType = MediaType.parse("application/zip")

    @Throws(IOException::class, InterruptedException::class)
    override fun invoke(f: File, channel: VirtualChannel)
    {
        // first check if asset exists
        val asset = release.assets.firstOrNull { it.name == f.name }

        if(asset != null)
        {
            // if the asset is the same length skip
            if(asset.size == f.length())
            {
                listener.logger.println("Asset ${asset.name} from ${release.name} already exists, skipping...")
                listener.logger.println("Please manually delete the asset if re-upload is required")
                return
            }

            // if the asset isn't of the same length exists delete it before continuing
            if(asset.size != f.length())
            {
                listener.logger.println("Deleting asset ${asset.name} from ${release.name}")
                asset.delete()
            }
        }

        val request = buildUploadRequest(release.uploadUrl, f)
        val response = client.newCall(request).execute()

        if(!response.isSuccessful)
        {
            with(listener)
            {
                error("Error uploading artifacts response code returned: ${response.code()} \n")
                response.body().use { error("Response body: ${it?.string()} \n") }
            }
            run.setResult(Result.FAILURE)
            return
        }

        listener.logger.println("Uploaded artifact ${f.name}")
    }

    private fun buildUploadRequest(uploadURL: String, artifact: File): Request
    {
        return Request.Builder()
            .url(createUploadURL(uploadURL, artifact.name))
            .post(RequestBody.create(archiveType, artifact))
            .addHeader("User-Agent", "Jenkins/${Jenkins.getVersion()}")
            .addHeader("Authorization", "token $apiToken")
            .addHeader("Content-Type", archiveType.toString())
            .build()
    }

    private fun createUploadURL(urlTemplate: String, artifactName: String): String
    {
        return urlTemplate.replace("{?name,label}", "?name=$artifactName")
    }

    @Throws(SecurityException::class)
    override fun checkRoles(checker: RoleChecker)
    {
    }
}
