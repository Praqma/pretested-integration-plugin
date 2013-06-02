package org.jenkinsci.plugins.pretestedintegration;

import java.lang.reflect.InvocationTargetException;

import org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial.HgUtils;

public class PretestedIntegrationHgUtilsTest extends PretestedIntegrationTestCase {

	public void testShouldCreateInstance() throws Exception {
		genericTestConstructor(HgUtils.class);
	}

	public void testshouldHaveNextCommit() throws Exception {

		File tmp = createTempDirectory();
		setupRepositoryDirectory(tmp);
		//touch file
		//write in file
		//add file
		//hg commit
		//assert that hasCommit returns true

		//hg("init", repoDir.getPath());
	}
}
