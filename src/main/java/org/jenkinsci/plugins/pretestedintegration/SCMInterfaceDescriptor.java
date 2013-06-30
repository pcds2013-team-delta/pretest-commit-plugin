package org.jenkinsci.plugins.pretestedintegration;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;

public class SCMInterfaceDescriptor<T extends AbstractSCMInterface> extends
		Descriptor<AbstractSCMInterface> {

	@Override
	public String getDisplayName() {
		// TODO Auto-generated method stub
		return null;
	}
	
	protected SCMInterfaceDescriptor(Class<? extends AbstractSCMInterface> clazz) {
        super(clazz);
    }

    public AbstractSCMInterface newInstance(StaplerRequest req, JSONObject formData) throws FormException {
    	return super.newInstance(req, formData);
    }
   
}
