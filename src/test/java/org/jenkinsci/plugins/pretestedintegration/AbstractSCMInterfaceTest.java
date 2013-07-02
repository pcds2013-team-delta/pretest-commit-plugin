package org.jenkinsci.plugins.pretestedintegration;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;

import java.util.List;

import org.jvnet.hudson.test.HudsonTestCase;
import static org.mockito.Mockito.*;

public class AbstractSCMInterfaceTest extends HudsonTestCase {

	public void testShouldIncludeDummySCMExtension() throws Exception {
		boolean includedInInterfaceDescriptors = false;
		boolean includedInDescriptors = false;
		List<SCMInterfaceDescriptor<AbstractSCMInterface>> interfaceDescriptors = AbstractSCMInterface.all();
		for(SCMInterfaceDescriptor<AbstractSCMInterface> i : interfaceDescriptors){
			if(i.getDisplayName().equals("DummySCM"))
				includedInInterfaceDescriptors = true;
		}
		assertTrue(includedInInterfaceDescriptors);
		
		List<SCMInterfaceDescriptor<?>> descriptors = AbstractSCMInterface.getDescriptors();
		for(SCMInterfaceDescriptor<?> d : descriptors){
			if(d.getDisplayName().equals("DummySCM"))
				includedInDescriptors = true;
		}
		assertTrue(includedInDescriptors);
	}
	
	public void testShouldGetCorrectDescriptor(){
		DummySCM scm = new DummySCM();
		assertEquals("DummySCM",scm.getDescriptor().getDisplayName());
	}
	
	public void testShouldBeCommited() throws Exception {
		DummySCM scm = new DummySCM();
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		when(build.getResult()).thenReturn(Result.SUCCESS);
		Launcher launcher = mock(Launcher.class);
		BuildListener listener = mock(BuildListener.class);
		assertFalse(scm.isCommited());
		scm.handlePostBuild(build, launcher, listener);
		assertTrue(scm.isCommited());
	}
	
	public void testShouldBeRolledBack() throws Exception {
		DummySCM scm = new DummySCM();
		FreeStyleBuild build = mock(FreeStyleBuild.class);
		when(build.getResult()).thenReturn(Result.UNSTABLE);
		Launcher launcher = mock(Launcher.class);
		BuildListener listener = mock(BuildListener.class);
		assertFalse(scm.isRolledBack());
		scm.handlePostBuild(build, launcher, listener);
		assertTrue(scm.isRolledBack());
	}
	
}
