package com.traveloka.jenkins.githubcheck;

import hudson.model.InvisibleAction;

public class CheckRunOutputAction extends InvisibleAction {
  private transient final CheckRunOutput output;

  public CheckRunOutputAction(CheckRunOutput output) {
    this.output = output;
  }

  public CheckRunOutput getOutput() {
    return output;
  }
}
