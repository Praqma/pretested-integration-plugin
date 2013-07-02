package org.jenkinsci.plugins.pretestedintegration.scm.mercurial;

import org.jenkinsci.plugins.pretestedintegration.AbstractSCMInterface;
import org.jenkinsci.plugins.pretestedintegration.SCMInterfaceDescriptor;
import org.jvnet.hudson.test.HudsonTestCase;
import static org.mockito.Mockito.*;

public class MercurialTest extends HudsonTestCase {

	public void testShouldBeExtension(){
		boolean inDescriptorList = false;
		for(SCMInterfaceDescriptor<AbstractSCMInterface> d : AbstractSCMInterface.all()) {
			if(d.getDisplayName().equals("Mercurial"))
				inDescriptorList = true;
		}
		assertTrue(inDescriptorList);
	}
	
}
