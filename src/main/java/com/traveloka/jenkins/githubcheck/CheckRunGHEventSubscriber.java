package com.traveloka.jenkins.githubcheck;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

import org.jenkinsci.plugins.github.extension.GHEventsSubscriber;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload.CheckRun;

import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;

/**
 * This subscriber manages {@link GHEvent} PULL_REQUEST_REVIEW edits.
 */
@Extension
public class CheckRunGHEventSubscriber extends GHEventsSubscriber {
  /**
   * Logger.
   */
  private static final Logger LOGGER = Logger.getLogger(CheckRunGHEventSubscriber.class.getName());

  /**
   * Regex pattern for a GitHub repository.
   */

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
    ObjectMapper mapper = new ObjectMapper();
    CheckRun payload;
    try {
      payload = mapper.readValue(payloadString, CheckRun.class);
    } catch (JsonProcessingException e) {
      LOGGER.log(Level.WARNING, "Can not decode event payload", e);
      return;
    }

    if (payload.getAction() != "rerequested") {
      LOGGER.info("Ignoring event " + payload.getAction());
      return;
    }

    String externalId = payload.getCheckRun().getExternalId();
    if (externalId.isEmpty()) {
      LOGGER.warning("Trying to rebuild invalid check");
      return;
    }

    CheckRunExternalId runPayload = CheckRunExternalId.fromString(externalId);

    try (ACLContext context = ACL.as(ACL.SYSTEM)) {
      WorkflowJob job = Jenkins.get().getItemByFullName(runPayload.job, WorkflowJob.class);
      if (job == null) {
        return;
      }
      WorkflowRun run = job.getBuildByNumber(runPayload.run);
      ReplayAction action = run.getAction(ReplayAction.class);
      action.run2(action.getOriginalScript(), action.getOriginalLoadedScripts());
    }
  }

}
