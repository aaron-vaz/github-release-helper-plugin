package uk.co.aaronvaz.jenkins.plugin

import hudson.model.FreeStyleProject
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule

@Ignore
class GithubReleaseCreatorTest
{
    @Rule
    var jenkinsRule = JenkinsRule()

    private var project: FreeStyleProject? = null

    @Before
    @Throws(Exception::class)
    fun setUp()
    {
        project = jenkinsRule.createFreeStyleProject()
        project!!.publishersList.add(GithubReleaseCreator("test-repo",
            "v1.0",
            "master",
            "Test Release",
            "Release Message",
            false,
            false,
            "*/*.zip"))
    }

    @Test
    @Throws(Exception::class)
    fun perform_RunPlugin_PluginRanSuccessfully()
    {
        // given

        // when
        val freeStyleBuild = project!!.scheduleBuild2(0).get()

        // then
        jenkinsRule.assertBuildStatusSuccess(freeStyleBuild)
    }
}
