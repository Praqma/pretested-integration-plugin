package org.jenkinsci.plugins.pretestedintegration;

import java.lang.reflect.InvocationTargetException;

public class PretestedIntegrationHgUtilsTest extends PretestedIntegrationTestCase {

	public void testShouldCreateInstance() throws Exception {
		genericTestConstructor(HgUtils.class);
	}
}
