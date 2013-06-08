package org.jenkinsci.plugins.pretestedintegration;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.Build;
import hudson.model.FreeStyleBuild;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.util.StreamTaskListener;

import java.lang.reflect.InvocationTargetException;

import org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial.HgUtils;
import org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial.MercurialIntegrationTest;
import org.jenkinsci.plugins.pretestedintegration.scminterface.PretestedIntegrationSCMCommit;
import org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial.PretestedIntegrationSCMMercurial;

import org.junit.*;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.jvnet.hudson.test.HudsonTestCase;
import hudson.plugins.mercurial.*;

import static org.mockito.Mockito.*;

public class PretestedIntegrationHgUtilsTest extends MercurialIntegrationTest {

}
