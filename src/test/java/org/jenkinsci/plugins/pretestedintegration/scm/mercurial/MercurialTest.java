package org.jenkinsci.plugins.pretestedintegration.scm.mercurial;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.jenkinsci.plugins.pretestedintegration.AbstractSCMInterface;
import org.jenkinsci.plugins.pretestedintegration.SCMInterfaceDescriptor;
import org.junit.Assert;
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
	
	public void testShouldInitialise() throws Exception {
		Mercurial mercurial = new Mercurial(true,"test");
		assertTrue(mercurial.getReset());
		assertEquals("test",mercurial.getBranch());
		
		mercurial = new Mercurial(false,"test");
		assertFalse(mercurial.getReset());
		assertEquals("test",mercurial.getBranch());
		
		mercurial = new Mercurial(false,"");
		assertFalse(mercurial.getReset());
		assertEquals("default",mercurial.getBranch());
		
		mercurial = new Mercurial(false,null);
		assertFalse(mercurial.getReset());
		assertEquals("default",mercurial.getBranch());
	}
	
}
