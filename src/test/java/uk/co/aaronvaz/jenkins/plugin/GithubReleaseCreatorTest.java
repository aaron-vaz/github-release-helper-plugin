package uk.co.aaronvaz.jenkins.plugin;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

@Ignore
public class GithubReleaseCreatorTest
{
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private FreeStyleProject project;

    @Before
    public void setUp()
        throws Exception
    {
        project = jenkinsRule.createFreeStyleProject();
        project.getPublishersList().add(new GithubReleaseCreator("test-repo",
            "v1.0",
            "master",
            "Test Release",
            "Release Message",
            false,
            false,
            "*/*.zip"));
    }

    @Test
    public void perform_RunPlugin_PluginRanSuccessfully()
        throws Exception
    {
        // given

        // when
        final FreeStyleBuild freeStyleBuild = project.scheduleBuild2(0).get();

        // then
        jenkinsRule.assertBuildStatusSuccess(freeStyleBuild);
    }
}
