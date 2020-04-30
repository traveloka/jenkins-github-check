package com.traveloka.jenkins.githubcheck;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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
  private static final Logger LOGGER = Logger.getLogger(WithCheckRunStep.class.getName());
  private final Arg arg;

  @DataBoundConstructor
  public WithCheckRunStep(String name, String outputFile, String title, String summary) {
    this.arg = new Arg(name, outputFile, title, summary);
  }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    return new Execution(arg, context);
  }

  public String getName() {
    return arg.name;
  }

  public String getOutputFile() {
    return arg.outputFile;
  }

  public String getTitle() {
    return arg.title;
  }

  public String getSummary() {
    return arg.summary;
  }

  private static class Arg implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String outputFile;
    private final String title;
    private final String summary;

    Arg(String name, String outputFile, String title, String summary) {
      this.name = name;
      this.outputFile = outputFile;
      this.title = title;
      this.summary = summary;
    }
  }

  public static class Execution extends AbstractStepExecutionImpl {
    private static final long serialVersionUID = 1;

    private final Arg arg;

    Execution(Arg arg, StepContext context) {
      super(context);
      this.arg = arg;
    }

    @Override
    public boolean start() throws Exception {
      StepContext context = getContext();

      Run<?, ?> run = context.get(Run.class);
      TaskListener listener = context.get(TaskListener.class);
      CheckRunHelper cr = new CheckRunHelper(run, listener);

      listener.getLogger().printf("Stating check %s", arg.name);
      Date startTime = new Date();
      cr.builder(arg.name, Status.IN_PROGRESS, null, readOutput(context, arg)).withStartedAt(startTime).create();

      getContext().newBodyInvoker().withCallback(new Callback(cr, arg, startTime)).start();

      return false;
    }

    @Override
    public void onResume() {
    }
  }

  private static CheckRunOutput readOutput(StepContext context, Arg arg) throws IOException, InterruptedException {
    CheckRunOutput output = new CheckRunOutput();

    if (arg.outputFile != null && !arg.outputFile.isEmpty()) {

      FilePath file = context.get(FilePath.class).child(arg.outputFile);
      if (file.exists()) {
        ObjectMapper mapper = new ObjectMapper();

        try (InputStream stream = file.read()) {
          output = mapper.readValue(stream, CheckRunOutput.class);
        }
      }
    }

    if (arg.title != null && !arg.title.isEmpty()) {
      output.title = arg.title;
    }
    if (arg.summary != null && !arg.summary.isEmpty()) {
      output.summary = arg.summary;
    }

    return output;
  }

  private static class Callback extends BodyExecutionCallback {
    private static final long serialVersionUID = 1L;
    private transient final CheckRunHelper crHelper;
    private final Arg arg;
    private final Date startTime;

    Callback(CheckRunHelper crHelper, Arg arg, Date startTime) {
      this.crHelper = crHelper;
      this.arg = arg;
      this.startTime = startTime;
    }

    @Override
    public void onSuccess(StepContext context, Object result) {
      createCheck(context, Conclusion.SUCCESS, null);
      context.onSuccess(result);
    }

    @Override
    public void onFailure(StepContext context, Throwable t) {
      context.onFailure(createCheck(context, Conclusion.FAILURE, t));
    }

    private Throwable createCheck(StepContext context, Conclusion conclusion, Throwable t) {
      try {
        CheckRunOutput output = readOutput(context, arg);
        if (t != null) {
          if (output.text == null) {
            output.text = "";
          } else {
            output.text += "\n\n";
          }
          output.text += "Build Error: " + t.getMessage();
        }
        crHelper.builder(arg.name, Status.COMPLETED, conclusion, output).withStartedAt(startTime)
            .withCompletedAt(new Date()).create();
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Can not create check run: IOException", e);
      } catch (InterruptedException e) {
        LOGGER.log(Level.WARNING, "Can not create check run: InterruptedException", e);
      }
      return t;
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
      return ImmutableSet.of(Run.class, TaskListener.class, FilePath.class);
    }

  }

}
