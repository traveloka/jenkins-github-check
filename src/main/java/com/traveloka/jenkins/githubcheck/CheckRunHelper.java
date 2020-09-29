package com.traveloka.jenkins.githubcheck;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.jenkinsci.plugins.github_branch_source.Connector;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.github_branch_source.PullRequestSCMRevision;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.AbstractFlowScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.github.GHCheckRun.Conclusion;
import org.kohsuke.github.GHCheckRun.Status;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GitHub;

import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.branch.BranchProperty;
import jenkins.branch.MultiBranchProject;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;

public class CheckRunHelper {
  private final static Logger LOGGER = Logger.getLogger(CheckRunHelper.class.getName());
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
        LOGGER.log(Level.WARNING, "Can not fetch scm revision: IOException", e);
      } catch (InterruptedException e) {
        LOGGER.log(Level.WARNING, "Can not fetch scm revision: InterruptedException", e);
      }
    }
    if (revision instanceof SCMRevisionImpl) {
      commitHash = ((SCMRevisionImpl) revision).getHash();
    } else if (revision instanceof PullRequestSCMRevision) {
      commitHash = ((PullRequestSCMRevision) revision).getPullHash();
    } else {
      LOGGER.log(Level.WARNING, "Invalid scm revision");
      return;
    }

    isValid = true;
  }

  void create(String checkName, Status status, Conclusion conclusion, CheckRunOutput output) throws IOException {
    builder(checkName, status, conclusion, output).create();
  }

  protected GHCheckRunBuilder builder(String checkName, Status status, Conclusion conclusion, CheckRunOutput output)
      throws IOException {
    if (!isValid) {
      throw new InvalidContextException();
    }

    CheckRunExternalId externalId = new CheckRunExternalId(job.getFullName(), run.number);

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

    builder.withExternalID(externalId.toString());
    builder.withDetailsURL(DisplayURLProvider.get().getRunURL(run));
    builder.withStatus(status);
    builder.add(output.toBuilder());
    if (status == Status.COMPLETED) {
      builder.withConclusion(conclusion);
    }
    return builder;
  }

  public static void flush(final Run<?, ?> run, final @Nonnull TaskListener listener) {
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
      GHCheckRunBuilder builder;
      CheckRunOutput output = new CheckRunOutput();
      CheckRunOutputAction action = run.getAction(CheckRunOutputAction.class);
      if (action != null) {
        output = action.getOutput();
      }

      if (result == null || run.isBuilding()) {
        builder = cr.builder(checkName, Status.IN_PROGRESS, null, output);
      } else {
        Conclusion conclusion = result.isBetterOrEqualTo(Result.SUCCESS) ? Conclusion.SUCCESS
            : result.isWorseOrEqualTo(Result.ABORTED) ? Conclusion.CANCELLED : Conclusion.FAILURE;

        if (conclusion != Conclusion.SUCCESS) {
          String cause = getRunError(run);
          if (cause != null && !cause.isEmpty()) {
            String currentText = output.text == null ? "" : (output.text + "\n\n");
            output.text = currentText + "**Error:**: " + cause;
          }
        }

        builder = cr.builder(checkName, Status.COMPLETED, conclusion, output)
            .withCompletedAt(new Date(run.getTimeInMillis() + run.getDuration()));
      }

      builder.withStartedAt(run.getTime()).create();

    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Error when creating github check run", e);
    }
  }

  static private String getRunError(Run<?, ?> run) {
    if (!(run instanceof WorkflowRun))
      return null;

    FlowExecution execution = ((WorkflowRun) run).getExecution();
    if (execution == null)
      return null;

    List<FlowNode> heads = execution.getCurrentHeads();

    AbstractFlowScanner scanner = new DepthFirstScanner();
    FlowNode node = scanner.findFirstMatch(heads, item -> {
      return item instanceof StepAtomNode && item.getError() != null;
    });
    if (node == null)
      node = heads.get(0);

    Throwable cause = node.getError().getError();
    if (cause == null)
      return null;

    String message = getNodeLabel(node) + ": " + cause.getLocalizedMessage();
    return message;
  }

  static private String getNodeLabel(FlowNode node) {
    LabelAction labelAction = node.getPersistentAction(LabelAction.class);
    if (labelAction != null) {
      return labelAction.getDisplayName();
    } else {
      Map<String, Object> argAction = ArgumentsAction.getFilteredArguments(node);
      Object labelArg = argAction.get("label");
      if (labelArg != null && labelArg instanceof String) {
        return (String) labelArg;
      }

      Object scriptArg = argAction.get("script");
      if (scriptArg != null && scriptArg instanceof String) {
        return "script(`" + (String) scriptArg + "`)";
      }
    }

    return node.getDisplayName();
  }

  public static class InvalidContextException extends IOException {
  }

}
