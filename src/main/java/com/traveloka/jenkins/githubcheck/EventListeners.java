package com.traveloka.jenkins.githubcheck;

import java.io.File;

import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;

public class EventListeners {
  @Extension
  public static class JobCheckOutListener extends SCMListener {

    @Override
    public void onCheckout(Run<?, ?> run, SCM scm, FilePath workspace, TaskListener listener, File changelogFile,
        SCMRevisionState pollingBaseline) throws Exception {
      CheckRunHelper.flush(run, listener);
    }

  }

  @Extension
  public static class JobCompletedListener extends RunListener<Run<?, ?>> {

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
      CheckRunHelper.flush(run, listener);
    }

    @Override
    public void onCompleted(final Run<?, ?> run, final @Nonnull TaskListener listener) {
      CheckRunHelper.flush(run, listener);
    }
  }

}
