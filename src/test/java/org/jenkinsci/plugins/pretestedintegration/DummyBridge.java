package org.jenkinsci.plugins.pretestedintegration;

import java.util.List;

public class DummyBridge extends AbstractSCMBridge {

    public DummyBridge(List<SCMPostBuildBehaviour> behaves) {
        super(behaves);
    }

}
