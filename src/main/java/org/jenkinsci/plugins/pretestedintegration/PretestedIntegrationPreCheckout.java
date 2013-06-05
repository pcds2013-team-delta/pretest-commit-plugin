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

import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 */
public class PretestedIntegrationPreCheckout extends BuildWrapper {
	
	private static final String DISPLAY_NAME = "Use pretested integration";
	private static final String PLUGIN_NAME = "pretested-integration";
	
	private final String stageRepositoryUrl;

	private boolean hasQueue;
	
	//TODO: Store as privates instead of passing around all the time
	private AbstractBuild build;
	private Launcher launcher;
	private BuildListener listener;
	
	@DataBoundConstructor
	public PretestedIntegrationPreCheckout(String stageRepositoryUrl) {
		this.stageRepositoryUrl = stageRepositoryUrl;
	}

	public String getStageRepositoryUrl() {
		return stageRepositoryUrl;
	}
	
	private String getScmRepositoryURL() throws AbortException {
		//Setup variables to find our executable
		AbstractProject<?,?> project = build.getProject();
		//We need to check this cast..
		MercurialSCM scm = null;
		try {
			scm = (MercurialSCM) project.getScm();
		} catch(ClassCastException e) {
			listener.error("The chosen SCM is not Mercurial!");
			throw new AbortException("The chosen SCM is not Mercurial!");
		}
		return scm.getSource();
	}
	
	void mergeWithNewBranch(String repositoryURL)
			throws IOException, InterruptedException {
		
		PretestUtils.logMessage(listener, "Pulling changes from stage");
		//First get the curent tip info
		Dictionary<String, String> oldVars = HgUtils.getNewestCommitInfo(build, launcher, listener);
		String oldTip = oldVars.get("changeset");
		
		try {
			HgUtils.runScmCommand(build, launcher, listener, 
					new String[]{"update","default"});
			HgUtils.runScmCommand(build, launcher, listener,
				new String[]{"pull", "--update", repositoryURL});
			
			
			Dictionary<String, String> newVars =  HgUtils.getNewestCommitInfo(build, launcher, listener);
			
			HgUtils.runScmCommand(build, launcher, listener,
				new String[]{"merge", newVars.get("branch")});
			HgUtils.runScmCommand(build, launcher, listener,
				new String[]{"commit", "-m", newVars.get("message")});
		} catch(AbortException e){
			System.out.print("Could not update workspace, uh oh!");
			throw e;
		}
	}
	
	/**
	 * Jenkins hook that fires after the workspace is initialized.
	 *
	 * @param build
	 * @param launcher
	 * @param listener
	 * @return noop Environment class
	 */
	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		this.build = build;
		this.launcher = launcher;
		this.listener = listener;

		PretestUtils.logMessage(listener, "Beginning pre-build step");
		
		// Get info about the newest commit and store it in the environment.
		// This is used to determine the triggering build, even if newer commits
		// are applied while this job is in the queue.
		Environment environment = new PretestEnvironment();
		environment.buildEnvVars(HgUtils.getNewestCommitInfo(
				build, launcher, listener)); //TODO this should be removed
		
		// Wait in line until no other jobs are running.
		CommitQueue.getInstance().enqueueAndWait();
		hasQueue = true;

		mergeWithNewBranch(stageRepositoryUrl);
		HgUtils.getNewestCommitInfo(build, launcher, listener);
		
		HgUtils.runScmCommand(build, launcher, listener, new String[]{"pull"});
		
		//Environment environment2 = build.getEnvironment(null);
		//environment2.put("stageRepositoryUrl",getStageRepositoryUrl());
		
		PretestUtils.logMessage(listener, "Finished pre-build step");
		
		return environment;
	}
	
	/**
	 * @param build
	 * @param launcher
	 * @param listener
	 */
	//@Override
	public void preCheckout() throws IOException, InterruptedException {
		if(Hudson.getInstance().getPlugin(PLUGIN_NAME) != null) {
			PretestUtils.logMessage(listener,"Pre-Checkout plugin version: "
					+ Hudson.getInstance().getPlugin(PLUGIN_NAME)
					.getWrapper().getVersion());
		}
		PretestUtils.logMessage(listener, "No plugin found with name "
				+ PLUGIN_NAME);
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
		
		/**
		 * Gets the configuration directory for a repository
		 * @param repositoryUrl
		 * @return
		 */
		public File configurationDirectory(final String repositoryUrl){
			return new File(repositoryUrl, ".hg");
		}
		
		/**
		 * Tests whether a repository exists at the specified location
		 * @assert That repositoryUrl is a local repository the user has access
		 * to
		 */
		public boolean validateConfiguration(final String repositoryUrl) {
			File repoDir = configurationDirectory(repositoryUrl);
			//We should check whether the hgrc file is correctly configured 
			//and that the python hook file is properly set
			//That the repository exists is good enough for now
			return repoDir.exists();
		}
		
		public boolean setupRepositoryDirectory(File repoDir) {
			return (repoDir.exists() || repoDir.mkdirs());
		}
		
		/**
		 * Updates the configuration file in the specified repository
		 * @param repositoryUrl
		 */
		public boolean writeConfiguration(final String repositoryUrl) {
			File repoDir = configurationDirectory(repositoryUrl);
			if(setupRepositoryDirectory(repoDir)){
				//the hgDir now is a valid path
				//Write the hgrc file
				try {
					File hgrc = new File(repoDir,"hgrc");
					if(hgrc.canWrite() || hgrc.createNewFile()){
						Ini ini = new Ini(hgrc);
						ini.put("hooks","pretxnchangegroup", 
							"python:.hg/hg_changegroup_hook.py:run");
						ini.store();
						return true;
					} else {
						return false;
					}
				} catch(InvalidFileFormatException e) {
					System.out.println("Invalid file format");
				} catch(IOException e) {	
					System.out.println("Some ioxception occured");
				}
			}
			return false;
		}
		
		public String getJenkinsRootUrl() {
			return Hudson.getInstance()
					.getRootUrl()
					.replaceAll("^http://|/$", "");
		}
		
		/**
		 * Updates the hook file in the specified repository
		 * @param repositoryUrl
		 * @return 
		 */
		public boolean writeHook(final File repoDir, final String jenkinsRoot, final String job) throws IOException {
			File hook = new File(repoDir,"hg_changegroup_hook.py");
			if(hook.canWrite() || hook.createNewFile()){
				FileWriter fw = new FileWriter(hook);
				fw.write("from mercurial import ui, hg\r\n");
				fw.write("from mercurial.node import hex\r\n");
				fw.write("from httplib import HTTPConnection\r\n");
				fw.write("from urllib import urlencode\r\n");
				fw.write("import os\r\n\r\n");
						
				fw.write("def run(ui, repo, **kwargs):\r\n");
				fw.write("\thttp = HTTPConnection(\"" 
						+ jenkinsRoot + "\")\r\n");
				fw.write("\thttp.request(\"GET\",\"http://" 
						+ jenkinsRoot + "/job/" 
						+ job + "/build\")\r\n");
				fw.write("\tui.warn(\"http://" 
						+ jenkinsRoot + "/job/" 
						+ job + "/build\\n\")\r\n");
				fw.write("\tui.warn(str(http.getresponse().read())"
						+ "+\"\\n\")\r\n");
				fw.write("\treturn False\r\n");
						
				fw.close();
				return true;
			}
			
			return false;
		}
		
		
		/**
		 * Performs a validation test whether the repository url is correctly 
		 * initialised
		 */
		public FormValidation doTestRepository(
				@QueryParameter("stageRepositoryUrl") final String repositoryUrl) 
						throws IOException, ServletException {
			boolean isValid = validateConfiguration(repositoryUrl);
			if(isValid) {
				return FormValidation.ok("The path contains a valid mercurial repository");
			} else {
				return FormValidation.error("The repository is not a mercurial repository");
			}
		}
		
		/**
		 * Updates or creates the repository url to be functional
		 * @param repositoryUrl
		 * @return
		 */
		public FormValidation doUpdateRepository(@QueryParameter("stageRepositoryUrl") final String repositoryUrl, 
				@QueryParameter("name") final String name) {
			boolean updateConfiguration = writeConfiguration(repositoryUrl);
			if(updateConfiguration){
				File configDir = configurationDirectory(repositoryUrl);
				if(setupRepositoryDirectory(configDir)){
					try {
						boolean updateHook = writeHook(configDir, getJenkinsRootUrl(), name);
						if(updateHook) {
							return FormValidation.ok("Configuration updated");
						}
					} catch (IOException e){
						
					}
				}
			}
			return FormValidation.error("Configuration could not be updated");
		}
		
		/**
		 * Invoked when "save" is hit. 
		 */
		
		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) 
				throws FormException {
			
			save();
			return super.configure(req,formData);
		}

		@Override
		public boolean isApplicable(AbstractProject<?, ?> arg0) {
			// TODO Auto-generated method stub
			return true;
		}
	}
	
	class NoopEnv extends Environment {
	}
	class PretestEnvironment extends Environment {
	}
}
