package org.jenkinsci.plugins.pretestedintegration;

import org.jvnet.hudson.test.HudsonTestCase;

public class CommitTest extends HudsonTestCase {
	public void testShouldIncludeDummyCommit() {
		DummyCommit c = Commit.all().get(DummyCommit.class);
		assertNotNull(c);
	}
	
	public void testShouldHaveCorrectId(){
		String id = "1234567";
		Commit<String> c = new DummyCommit(id);
		assertEquals(id,c.getId());
	}
}
