package org.jenkinsci.plugins.pretestedintegration;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

//import org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial.HgUtils;

/**
 * The build wrapper determines what will happen before the build will run.
 * Depending on the chosen SCM, a more specific function will be called.
 */
public class PretestedIntegrationPreCheckout extends BuildWrapper {
	
	private static final String DISPLAY_NAME = "Use pretested integration";
	private static final String PLUGIN_NAME = "pretested-integration";
	
	private AbstractSCMInterface scmInterface;
	
/*	@DataBoundConstructor
	public PretestedIntegrationPreCheckout(Map<Descriptor, Describable> instances) {
		this.instances = instances;
	}*/
	
	@DataBoundConstructor
	public PretestedIntegrationPreCheckout(AbstractSCMInterface scmInterface){
		this.scmInterface = scmInterface;
	}
	
	public AbstractSCMInterface getScmInterface(){
		return this.scmInterface;
	}
	/*
	public Map<Descriptor, Describable> getInstances(){
		return this.instances;
	}*/
	
	
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

		//PretestUtils.logMessage(listener, "Beginning pre-build step");
		
		PretestedIntegrationAction action = new PretestedIntegrationAction(build, launcher, listener, scmInterface);
		build.addAction(action);
		boolean result = action.initialise();
		
		if(!result)
			build.setResult(Result.NOT_BUILT);
		
		//PretestUtils.logMessage(listener, "Finished pre-build step");
		
		Environment environment = new PretestEnvironment();
		return environment;
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
			PretestedIntegrationPreCheckout b = (PretestedIntegrationPreCheckout) super.newInstance(req,formData);
			
			SCMInterfaceDescriptor<AbstractSCMInterface> d = (SCMInterfaceDescriptor<AbstractSCMInterface>) b.getScmInterface().getDescriptor();
			b.scmInterface = d.newInstance(req, formData);

//			List<AbstractSCMInterface> is = new ArrayList<AbstractSCMInterface>();
			/*Map<Descriptor,Describable> instances = new HashMap<Descriptor,Describable>();
			List<SCMInterfaceDescriptor<?>> descriptors = getSCMs();
			Iterator<SCMInterfaceDescriptor<?>> it = descriptors.iterator();
			
			while(it.hasNext()){
				SCMInterfaceDescriptor<?> d = it.next();
				instances.put(d, d.newInstance(req,formData));
			}
			b.instances = instances;*/
			save();
			return b;
		}

		public List<SCMInterfaceDescriptor<?>>getSCMInterfaces(){
			return AbstractSCMInterface.getDescriptors();
		}
		
		public ListBoxModel doFillScmInterfaceItems() {
			ListBoxModel items = new ListBoxModel();
			for(Descriptor d : getSCMInterfaces()){
				items.add(d.getDisplayName(),d.getId());
			}
			return items;
		}
		
		@Override
		public boolean isApplicable(AbstractProject<?, ?> arg0) {
			return true;
		}
	}
	
	class PretestEnvironment extends Environment {
	}
}
