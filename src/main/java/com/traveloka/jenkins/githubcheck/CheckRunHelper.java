package com.traveloka.jenkins.githubcheck;

import java.io.IOException;

import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.jenkinsci.plugins.github_branch_source.Connector;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.github_branch_source.PullRequestSCMRevision;
import org.kohsuke.github.GHCheckRun.Conclusion;
import org.kohsuke.github.GHCheckRun.Status;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GitHub;

import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.branch.MultiBranchProject;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;

public class CheckRunHelper {
  boolean isValid = false;

  Job<?, ?> job;
  MultiBranchProject parentJob;
  GitHubSCMSource source;
  String commitHash;

  private final Run<?, ?> run;

  public CheckRunHelper(Run<?, ?> run, TaskListener listener) {
    this.run = run;

    job = run.getParent();
    parentJob = (MultiBranchProject) job.getParent();

    SCMSource src = SCMSource.SourceByItem.findSource(job);
    if (!(src instanceof GitHubSCMSource)) {
      return;
    }
    source = (GitHubSCMSource) src;

    if (source.getCredentialsId() == null) {
      return;
    }

    SCMRevision revision = SCMRevisionAction.getRevision(src, run);
    if (revision == null) {
      SCMHead head = SCMHead.HeadByItem.findHead(job);
      try {
        revision = source.fetch(head, listener);
      } catch (IOException e) {
        // ignore error
      } catch (InterruptedException e) {
        // ignore error
      }
    }
    if (revision instanceof SCMRevisionImpl) {
      commitHash = ((SCMRevisionImpl) revision).getHash();
    } else if (revision instanceof PullRequestSCMRevision) {
      commitHash = ((PullRequestSCMRevision) revision).getPullHash();
    } else {
      // can not get commitHash
      return;
    }

    isValid = true;
  }

  void create(String checkName, Status status, Conclusion conclusion, CheckRunOutput output) throws IOException {
    builder(checkName, status, conclusion, output).create();
  }

  GHCheckRunBuilder builder(String checkName, Status status, Conclusion conclusion, CheckRunOutput origingalOutput)
      throws IOException {

    String externalId = new CheckRunExternalId(job.getFullName(), run.number).toString();

    CheckRunOutput output = origingalOutput == null ? new CheckRunOutput() : origingalOutput;
    if (output.title == null || output.title.isEmpty()) {
      output.title = run.getFullDisplayName();
    }
    if (output.summary == null || output.summary.isEmpty()) {
      if (status == Status.COMPLETED) {
        switch (conclusion) {
          case SUCCESS:
            output.summary = Messages.CheckRunHelper_SuccessSummary();
            break;

          case FAILURE:
            output.summary = Messages.CheckRunHelper_FailedSummary();
            break;

          case CANCELLED:
            output.summary = Messages.CheckRunHelper_CanceledSummary();
            break;

          default:
            output.summary = Messages.CheckRunHelper_UnknownSummary();
        }
      } else {
        output.summary = Messages.CheckRunHelper_RunningSummary();
      }
    }

    GitHub github = Connector.connect(source.getApiUri(),
        Connector.lookupScanCredentials(job, source.getApiUri(), source.getCredentialsId()));

    GHCheckRunBuilder builder = github.getRepository(source.getRepoOwner() + "/" + source.getRepository())
        .createCheckRun(checkName, commitHash);

    builder.withExternalID(externalId);
    builder.withDetailsURL(DisplayURLProvider.get().getRunURL(run));
    builder.withStatus(status);
    builder.add(output.toBuilder());
    if (status == Status.COMPLETED) {
      builder.withConclusion(conclusion);
    }
    return builder;
  }
}
