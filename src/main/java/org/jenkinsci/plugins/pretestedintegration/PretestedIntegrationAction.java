package org.jenkinsci.plugins.pretestedintegration;

import java.io.IOException;

import hudson.AbortException;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

public class PretestedIntegrationAction implements Action {

	AbstractBuild<?, ?> build;
	Launcher launcher;
	BuildListener listener;
	AbstractSCMInterface scmInterface;
	Commit<?> last;
	Commit<?> commit;
	
	public PretestedIntegrationAction(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, AbstractSCMInterface scmInterface) throws IllegalArgumentException, IOException {
		this.build = build;
		this.launcher = launcher;
		this.listener = listener;
		this.scmInterface = scmInterface;
		this.commit = scmInterface.nextCommit(build, launcher, listener, last);
	}

	public String getDisplayName() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getIconFileName() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public String getUrlName() {
		return "pretested-integration";
	}
	
	public Commit<?> getCommit() {
		return this.commit;
	}
	
	/**
	 * Invoked before the build is started, responsible for preparing the workspace
	 * 
	 * @return True if any changes are made and the workspace has been prepared, false otherwise
	 * @throws IOException 
	 * @throws AbortException 
	 * @throws IllegalArgumentException 
	 */
	public boolean initialise() throws IllegalArgumentException, AbortException, IOException{
		boolean result = false;

		Commit<?> next = scmInterface.nextCommit(build, launcher, listener, commit);
		if(next != null){
			result = true;
			scmInterface.prepareWorkspace(build, launcher, listener, next);
		}
		return result;
	}
	
	/**
	 * Invoked by the notifier, responsible for commiting or rolling back the workspace
	 * @throws IllegalArgumentException
	 * @throws IOException
	 */

	public void finalise() throws IllegalArgumentException, IOException{
		
		scmInterface.handlePostBuild(build, launcher, listener);

		scmInterface.getDescriptor().save();
		
		//Trigger a new build if there are more commits
		Commit<?> next = scmInterface.nextCommit(build, launcher, listener, getCommit());
		if(next != null){
			build.getProject().scheduleBuild2(0);
		} 
	}

}
