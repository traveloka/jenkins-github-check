package com.traveloka.jenkins.githubcheck;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.kohsuke.github.GHCheckRun.Conclusion;
import org.kohsuke.github.GHCheckRun.Status;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import jenkins.branch.BranchProperty;

public class EventListeners {

  private static final Logger LOGGER = Logger.getLogger(EventListeners.class.getName());

  @Extension
  public static class JobCheckOutListener extends SCMListener {

    @Override
    public void onCheckout(Run<?, ?> run, SCM scm, FilePath workspace, TaskListener listener, File changelogFile,
        SCMRevisionState pollingBaseline) throws Exception {
      updateCheckRun(run, listener);
    }

  }

  @Extension
  public static class JobCompletedListener extends RunListener<Run<?, ?>> {

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
      updateCheckRun(run, listener);
    }

    @Override
    public void onCompleted(final Run<?, ?> run, final @Nonnull TaskListener listener) {
      updateCheckRun(run, listener);
    }
  }

  private static void updateCheckRun(final Run<?, ?> run, final @Nonnull TaskListener listener) {
    if (run == null) {
      return;
    }

    CheckRunHelper cr = new CheckRunHelper(run, listener);
    if (!cr.isValid) {
      return;
    }

    boolean propFound = false;
    String checkName = "";
    for (BranchProperty prop : cr.parentJob.getProjectFactory().getBranch(cr.job).getProperties()) {
      if ((prop instanceof CheckRunBranchProperty)) {
        checkName = ((CheckRunBranchProperty) prop).getCheckName().trim();
        propFound = true;
        break;
      }
    }

    if (!propFound) {
      return;
    }

    if (checkName.isEmpty()) {
      checkName = cr.parentJob.getName();
    }

    Result result = run.getResult();
    try {
      if (result == null || run.isBuilding()) {
        cr.create(checkName, Status.IN_PROGRESS, null, null);
      } else {
        Conclusion conclusion = result.isBetterOrEqualTo(Result.SUCCESS) ? Conclusion.SUCCESS
            : result.isWorseOrEqualTo(Result.ABORTED) ? Conclusion.CANCELLED : Conclusion.FAILURE;

        CheckRunOutput output = null;
        CheckRunOutputAction action = run.getAction(CheckRunOutputAction.class);
        if (action != null) {
          output = action.getOutput();
        }

        cr.create(checkName, Status.COMPLETED, conclusion, output);
      }
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Error when creating github check run", e);
    }
  }

}
