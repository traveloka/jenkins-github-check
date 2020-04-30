package com.traveloka.jenkins.githubcheck;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;

import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.github.GHCheckRun.Conclusion;
import org.kohsuke.github.GHCheckRun.Status;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;

public class WithCheckRunStep extends Step {
  private final String name;
  private final String outputFile;

  @DataBoundConstructor
  public WithCheckRunStep(String name, String outputFile) {
    this.name = name;
    this.outputFile = outputFile;
  }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    return new Execution(name, outputFile, context);
  }

  public static class Execution extends AbstractStepExecutionImpl {
    private static final long serialVersionUID = 1;

    private final String name;
    private final String outputFile;

    Execution(String name, String outputFile, StepContext context) {
      super(context);
      this.name = name;
      this.outputFile = outputFile;
    }

    public String getName() {
      return name;
    }

    public String getOutputFile() {
      return outputFile;
    }

    @Override
    public boolean start() throws Exception {
      StepContext context = getContext();

      Run<?, ?> run = context.get(Run.class);
      TaskListener listener = context.get(TaskListener.class);
      CheckRunHelper cr = new CheckRunHelper(run, listener);

      Date startTime = new Date();
      cr.builder(name, Status.IN_PROGRESS, null, readOutput(context, outputFile)).withStartedAt(startTime).create();

      getContext().newBodyInvoker().withCallback(new Callback(cr, name, outputFile, startTime)).start();

      return false;
    }

    @Override
    public void onResume() {
    }

  }

  private static CheckRunOutput readOutput(StepContext context, String outputFile)
      throws IOException, InterruptedException {
    if (outputFile != null && !outputFile.isEmpty()) {

      FilePath file = context.get(FilePath.class).child(outputFile);
      if (file.exists()) {
        ObjectMapper mapper = new ObjectMapper();

        try (InputStream stream = file.read()) {
          return mapper.readValue(stream, CheckRunOutput.class);
        }
      }
    }

    return new CheckRunOutput();
  }

  private static class Callback extends BodyExecutionCallback {
    private static final long serialVersionUID = 1L;
    private transient final CheckRunHelper crHelper;
    private final String outputFile;
    private final String name;
    private final Date startTime;

    Callback(CheckRunHelper crHelper, String name, String outputFile, Date startTime) {
      this.crHelper = crHelper;
      this.name = name;
      this.outputFile = outputFile;
      this.startTime = startTime;
    }

    @Override
    public void onSuccess(StepContext context, Object result) {
      createCheck(context, Conclusion.SUCCESS, null);
      context.onSuccess(result);
    }

    @Override
    public void onFailure(StepContext context, Throwable t) {
      createCheck(context, Conclusion.FAILURE, t);
      context.onFailure(t);
    }

    private void createCheck(StepContext context, Conclusion conclusion, Throwable t) {
      try {
        CheckRunOutput output = readOutput(context, outputFile);
        if (t != null) {
          if (output.text == null) {
            output.text = "";
          } else {
            output.text += "\n\n";
          }
          output.text += "Build Error: " + t.getMessage();
        }
        crHelper.builder(name, Status.COMPLETED, conclusion, output).withStartedAt(startTime)
            .withCompletedAt(new Date()).create();
      } catch (IOException e) {
        e.printStackTrace();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  @Extension
  public static class DescriptorImpl extends StepDescriptor {
    @Override
    public String getFunctionName() {
      return "withGitHubCheck";
    }

    @Override
    public String getDisplayName() {
      return "Wrap steps inside github check";
    }

    @Override
    public boolean takesImplicitBlockArgument() {
      return true;
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      // Set<Class<?>> set = Collections.emptySet();
      // set.add(TaskListener.class);
      // set.add(Run.class);
      // return set;
      return ImmutableSet.of(Run.class, TaskListener.class, FilePath.class);
    }

  }

}
