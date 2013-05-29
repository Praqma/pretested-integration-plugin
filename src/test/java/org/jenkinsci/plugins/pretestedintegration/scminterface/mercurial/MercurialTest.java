package org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MercurialTest {

	@Test
	public void shouldCreateInstance() throws Exception {
		genericTestConstructor(PretestedIntegrationSCMMercurial.class);
	}
	
	public static Object genericTestConstructor(final Class<?> cls) 
			   throws InstantiationException, IllegalAccessException, InvocationTargetException { 
		final Constructor<?> c = cls.getDeclaredConstructors()[0]; 
		c.setAccessible(true); 
		final Object n = c.newInstance((Object[])null); 
		
		Assert.assertNotNull(n); 
		return n; 
	}
}
