package com.traveloka.jenkins.githubcheck;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.collect.Sets;

import org.jenkinsci.plugins.github.extension.GHEventsSubscriber;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload.CheckRun;
import org.kohsuke.github.GitHub;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;

@Extension
public class CheckRunGHEventSubscriber extends GHEventsSubscriber {
  /**
   * Logger.
   */
  private static final Logger LOGGER = Logger.getLogger(CheckRunGHEventSubscriber.class.getName());

  @Override
  protected boolean isApplicable(Item item) {
    if (item != null) {
      SCMSourceOwner owner;
      if (item instanceof SCMSourceOwner) {
        owner = (SCMSourceOwner) item;
      } else if (item.getParent() instanceof SCMSourceOwner) {
        owner = (SCMSourceOwner) item.getParent();
      } else {
        return false;
      }

      for (SCMSource source : owner.getSCMSources()) {
        if (source instanceof GitHubSCMSource) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  protected Set<GHEvent> events() {
    return Sets.immutableEnumSet(GHEvent.CHECK_RUN);
  }

  @Override
  protected void onEvent(final GHEvent event, final String payloadString) {
    CheckRun payload;
    try {
      payload = GitHub.offline().parseEventPayload(new StringReader(payloadString), CheckRun.class);
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Cannot parse github webhook payload", e);
      return;
    }

    if (!"rerequested".equals(payload.getAction())) {
      LOGGER.log(Level.FINER, "Ignoring event " + payload.getAction());
      return;
    }

    CheckRunExternalId id = CheckRunExternalId.fromString(payload.getCheckRun().getExternalId());
    if (id == null) {
      LOGGER.warning("Trying to rebuild invalid check");
      return;
    }

    LOGGER.fine(String.format("rebuilding %s #%d", id.job, id.run));

    try (ACLContext context = ACL.as(ACL.SYSTEM)) {
      Jenkins jenkins = Jenkins.getInstanceOrNull();
      WorkflowJob job = jenkins.getItemByFullName(id.job, WorkflowJob.class);
      if (job == null) {
        LOGGER.warning("Can not found job with name " + id.job);
        return;
      }
      WorkflowRun run = job.getBuildByNumber(id.run);
      if (run == null) {
        LOGGER.warning(String.format("Can not found build %s #%d", id.job, id.run));
        return;
      }

      List<Action> actions = new ArrayList<>();
      actions.add(new CauseAction(new CheckRunRerequestedCause(payload.getCheckRun().getHtmlUrl())));
      actions.addAll(run.getActions(ParametersAction.class));
      actions.addAll(run.getActions(SCMRevisionAction.class));

      ParameterizedJobMixIn.scheduleBuild2(job, 0, actions.toArray(new Action[actions.size()]));
    }
  }

  public static class CheckRunRerequestedCause extends Cause {

    private final URL url;

    public CheckRunRerequestedCause(URL url) {
      this.url = url;
    }

    @Override
    public String getShortDescription() {
      return String.format("<a href=\"%s\">GitHub Check Run</a> rerequested.", url.toString());
    }

    @Override
    public void print(TaskListener listener) {
      listener.getLogger().printf("GitHub Check Run rerequested: %s", url.toString());
    }
  }
}
