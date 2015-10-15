package org.jenkinsci.plugins.pretestedintegration.unit;

import org.jenkinsci.plugins.pretestedintegration.Commit;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

public class CommitTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    public void testShouldIncludeDummyCommit() {
        DummyCommit c = Commit.all().get(DummyCommit.class);
        assertNotNull(c);
    }

    @Test
    public void testShouldHaveCorrectId() {
        String id = "1234567";
        Commit<String> c = new DummyCommit(id);
        assertEquals(id, c.getId());
    }
}
