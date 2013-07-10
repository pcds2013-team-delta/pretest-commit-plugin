package org.jenkinsci.plugins.pretestedintegration;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * The build wrapper determines what will happen before the build will run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationBuildWrapper extends BuildWrapper {
	
	private AbstractSCMInterface scmInterface;
	
	@DataBoundConstructor
	public PretestedIntegrationBuildWrapper(AbstractSCMInterface scmInterface){
		this.scmInterface = scmInterface;
	}
	
	public AbstractSCMInterface getScmInterface(){
		return this.scmInterface;
	}
	
	/**
	 * Jenkins hook that fires after the workspace is initialized.
	 * Calls the SCM-specific function according to the chosen SCM.
	 * 
	 * @param build
	 * @param launcher
	 * @param listener
	 * @return 
	 */
	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		logger.finest("Entering setUp");
		PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
		build.addAction(action);
		boolean result = action.initialise(launcher, listener);
		
		if(!result) {
			logger.finest("Set result to NOT_BUILT");
			build.setResult(Result.NOT_BUILT);
		}
		
		ensurePublisher(build);
		
		logger.finest("Exiting setUp");
		
		Environment environment = new PretestEnvironment();
		return environment;
	}
	
	public void ensurePublisher(AbstractBuild<?,?> build) throws IOException {
		Describable<?> describable = build.getProject().getPublishersList().get(PretestedIntegrationPostCheckout.class);
		if(describable == null) {
			logger.info("Adding publisher to project");
			build.getProject().getPublishersList().add(new PretestedIntegrationPostCheckout());
		}
	}
	/**
	 * Prints out version information.
	 * 
	 * @param build
	 * @param launcher
	 * @param listener
	 */
	//@Override
	public void preCheckout() throws IOException, InterruptedException {
		//nop
	}
	
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl)super.getDescriptor();
	}
	
	@Extension
	public static class DescriptorImpl extends BuildWrapperDescriptor {
		
		public String getDisplayName() {
			return "Use pretested integration";
		}
		
		public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			PretestedIntegrationBuildWrapper b = (PretestedIntegrationBuildWrapper) super.newInstance(req,formData);
			
			SCMInterfaceDescriptor<AbstractSCMInterface> d = (SCMInterfaceDescriptor<AbstractSCMInterface>) b.getScmInterface().getDescriptor();
			b.scmInterface = d.newInstance(req, formData);

			save();
			return b;
		}

		public List<SCMInterfaceDescriptor<?>>getSCMInterfaces(){
			return AbstractSCMInterface.getDescriptors();
		}
		
		@Override
		public boolean isApplicable(AbstractProject<?, ?> arg0) {
			return true;
		}
	}
	
	class PretestEnvironment extends Environment {
	}
	
	private static Logger logger = Logger.getLogger(PretestedIntegrationBuildWrapper.class.getName());
}
