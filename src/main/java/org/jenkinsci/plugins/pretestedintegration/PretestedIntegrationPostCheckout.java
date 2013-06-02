package org.jenkinsci.plugins.pretestedintegration;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Cause.LegacyCodeCause;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildStep;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.BuildStepMonitor;
import hudson.model.*;
import hudson.plugins.mercurial.*;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildStep;
import hudson.util.ArgumentListBuilder;
import hudson.FilePath;

import java.io.IOException;
import java.io.PrintStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Dictionary;
import java.io.BufferedReader;

import org.jenkinsci.plugins.pretestedintegration.CommitQueue;

import org.kohsuke.stapler.DataBoundConstructor;

import org.jenkinsci.plugins.pretestedintegration.scminterface.mercurial.HgUtils;


/**
 * A collection of funtions used post build.
 */
public class PretestedIntegrationPostCheckout extends Publisher {
	
	private static final String DISPLAY_NAME = "Run pretested integration post-build step";
	
	private boolean hasQueue;
	
	@DataBoundConstructor
	public PretestedIntegrationPostCheckout() {
	}

	@Override
	public boolean needsToRunAfterFinalized() {
		return true;
	}
	
	/**
	 * Pushing to Company Truth
	 * 
	 * @param build
	 * @param launcher
	 * @param listener
	 *
	 * @return void	 
	 */
	private void pushToCT(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		ArgumentListBuilder cmd = HgUtils.createArgumentListBuilder(
				build, launcher, listener);
		//get info regarding which branch that is going to be pushed to company truth	
		//Dictionary<String, String> newCommitInfo = HgUtils.getNewestCommitInfo(
		//		build, launcher, listener);
		//String sourceBranch = newCommitInfo.get("branch");
		//PretestUtils.logMessage(listener, "commit is on this branch: "
		//		+ sourceBranch);
		Dictionary<String, String> vars = null;
		vars =  HgUtils.getNewestCommitInfo(build, launcher, listener);

		try{
		HgUtils.runScmCommand(build, launcher, listener,
				new String[]{"commit", "-m", vars.get("message")});

		HgUtils.runScmCommand(build, launcher, listener,
				new String[]{"push", "--new-branch"});
		}
		catch(AbortException e){
			throw e;
		}
	}
	
	/**
	 * Determains the outcome of the build
	 * 
	 * @param build
	 * @param launcher
	 * @param listener
	 *
	 * @return boolean	 
	 */
	private boolean getBuildSuccessStatus(AbstractBuild build,
			Launcher launcher, BuildListener listener) {
		boolean success = true;
		try {
			BufferedReader br = new BufferedReader(build.getLogReader());
			while(success) {
				String line = br.readLine();
				if(line == null) {
					break;
				}
				if(line.startsWith("Build step '")
						&& line.endsWith("' marked build as failure")) {
							success = false;
					break;
				}
			}
		} catch(IOException e) {
			PretestUtils.logMessage(listener,
					"Could not read log. Assuming build failure.");
			success = false;
		}
		return success;
	}
	
	/**
	 * Overridden setup returns a noop class as we don't want to add anything
	 * here.
	 *
	 * @param build
	 * @param launcher
	 * @param listener
	 *
	 * @return boolean
	 */
	@Override
	public boolean perform(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		Result buildResult = build.getResult();
		if(buildResult.isWorseThan(Result.SUCCESS)) {
			PretestUtils.logMessage(listener,
					"Build result is worse than success. Aborting.");
			return false;
		}
		
		try {
			return work(build, launcher, listener);
		} catch(IOException e) {
			if (hasQueue) {
				CommitQueue.getInstance().release();
			}
			throw(e);
			//return false;
		} catch(InterruptedException e) {
			if (hasQueue) {
				CommitQueue.getInstance().release();
			}
			throw(e);
			//return false;
		} catch(Exception e) {
			if (hasQueue) {
				CommitQueue.getInstance().release();
			}
			e.printStackTrace();
			return false;
		}
	}
		
	/**
	 * Gets work from the queue
	 * 
	 * @param build
	 * @param launcher
	 * @param listener
	 *
	 * @return boolean	 
	 */
	public boolean work(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		PretestUtils.logMessage(listener, "Beginning post-build step");
		hasQueue = true;
		BufferedReader br = new BufferedReader(build.getLogReader());
		boolean status = getBuildSuccessStatus(build, launcher, listener);
		
		if(status) {
			PretestUtils.logMessage(listener,
					"Pushing resulting workspace to CT");
			pushToCT(build, launcher, listener);
		} else {
			//HgUtils.runScmCommand(build, launcher, listener, 
					//new String[]{"update","-C",oldTip});
			PretestUtils.logMessage(listener, "Build error. Not pushing to CT");
		}
		
		PretestUtils.logDebug(listener, "Queue available pre release: " +
				CommitQueue.getInstance().available());
		CommitQueue.getInstance().release();
		hasQueue = false;
		PretestUtils.logDebug(listener, "Queue available post release: " +
				CommitQueue.getInstance().available());
		
		PretestUtils.logMessage(listener, "Finished post-build step");
		
		return true;
	}
	
	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.BUILD;
	}
	
	@Extension
	public static final class DescriptorImpl extends Descriptor<Publisher> {
		public String getDisplayName() {
			return DISPLAY_NAME;
		}
	}
}
