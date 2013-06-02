package org.jenkinsci.plugins.pretestedintegration;

import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Dictionary;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pretestedintegration.CommitQueue;
import org.jenkinsci.plugins.pretestedintegration.scminterface
		.PretestedIntegrationSCMInterface;
import org.jenkinsci.plugins.pretestedintegration.scminterface
		.AvailableInterfaces;

import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;
import org.ini4j.Wini;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 */
public class PretestedIntegrationPreCheckout extends BuildWrapper {
	
	private static final String DISPLAY_NAME = "Use pretested integration";
	private static final String PLUGIN_NAME = "pretested-integration";
	
	private boolean hasQueue;
	
	private String getScmType(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws AbortException {
		AbstractProject<?,?> project = build.getProject();
		return project.getScm().getType();
	}
	
	@DataBoundConstructor
	public PretestedIntegrationPreCheckout() {
	}
	
	/**
	 * Jenkins hook that fires after the workspace is initialized.
	 * 
	 * @param build
	 * @param launcher
	 * @param listener
	 * @return 
	 */
	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		PretestUtils.logMessage(listener, "Beginning pre-build step");
		
		// Wait in line until no other jobs are running.
		CommitQueue.getInstance().enqueueAndWait();
		hasQueue = true;
		
		// Get the interface for the SCM according to the chosen SCM
		String scmType = getScmType(build, launcher, listener);
		if(scmType == null) {
			PretestUtils.logMessage(
					listener, "No SCM chosen");
			return null;
		}
		Class scmClass = AvailableInterfaces.getClassByName(scmType);
		if(scmClass == null) {
			PretestUtils.logMessage(
					listener, "No interface found for SCM type: " + scmType);
			return null;
		}
		PretestedIntegrationSCMInterface scmInterface;
		try {
			scmInterface = (PretestedIntegrationSCMInterface)
					scmClass.newInstance();
		} catch(InstantiationException e) {
			PretestUtils.logMessage(listener, "Could not instantiate class: "
					+ AvailableInterfaces.getClassByName(scmType));
			return null;
		} catch(IllegalAccessException e) {
			PretestUtils.logMessage(listener, "Could not instantiate class: "
					+ AvailableInterfaces.getClassByName(scmType));
			return null;
		} catch(ClassCastException e) {
			PretestUtils.logMessage(listener, "SCM interface class does not"
					+ " implement the PretestedIntegrationSCMInterface: "
					+ AvailableInterfaces.getClassByName(scmType));
			return null;
		}
		
		// Verify that there is anything to do
		if(!scmInterface.hasNextCommit(build, launcher, listener)) {
			PretestUtils.logMessage(listener, "Nothing to build. Aborting.");
			return null;
		}
		
		// Prepare for build
		scmInterface.prepareWorkspace(build, launcher, listener,
				scmInterface.popCommit(build, launcher, listener));
		
		PretestUtils.logMessage(listener, "Finished pre-build step");
		
		Environment environment = new PretestEnvironment();
		return environment;
	}
	
	/**
	 * @param build
	 * @param launcher
	 * @param listener
	 */
	@Override
	public void preCheckout(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		if(Hudson.getInstance().getPlugin(PLUGIN_NAME) != null) {
			PretestUtils.logMessage(listener, PLUGIN_NAME + " plugin version: "
					+ Hudson.getInstance().getPlugin(PLUGIN_NAME)
					.getWrapper().getVersion());
		} else {
			PretestUtils.logMessage(listener, "No plugin found with name "
					+ PLUGIN_NAME);
		}
	}
	
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl)super.getDescriptor();
	}
	
	@Extension
	public static class DescriptorImpl extends BuildWrapperDescriptor {
		
		public String getDisplayName() {
			return DISPLAY_NAME;
		}
		
		@Override
		public boolean isApplicable(AbstractProject<?, ?> arg0) {
			// TODO Auto-generated method stub
			return true;
		}
	}
	
	class PretestEnvironment extends Environment {
	}
}
