package com.traveloka.jenkins.githubcheck;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;

public class SetCheckRunOutputStep extends Builder implements SimpleBuildStep {

    private String file;
    private String title;
    private String summary;
    private String text;
    private boolean flush;

    @DataBoundConstructor
    public SetCheckRunOutputStep() {
        // nothing to do here
    }

    @DataBoundSetter
    public void setFile(String file) {
        this.file = file;
    }

    @DataBoundSetter
    public void setTitle(String title) {
        this.title = title;
    }

    @DataBoundSetter
    public void setSummary(String summary) {
        this.summary = summary;
    }

    @DataBoundSetter
    public void setText(String text) {
        this.text = text;
    }

    @DataBoundSetter
    public void setFlush(boolean flush) {
        this.flush = flush;
    }

    public String getFile() {
        return file;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getText() {
        return text;
    }

    public boolean getFlush() {
        return flush;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {

        CheckRunOutput output = new CheckRunOutput();
        CheckRunOutputAction action = run.getAction(CheckRunOutputAction.class);
        if (action != null) {
            output = action.getOutput();
        }
        if (file != null && !file.isEmpty()) {
            FilePath filePath = workspace.child(file);
            listener.getLogger().printf("Using %s as github check output\n", filePath.toURI());
            ObjectMapper mapper = new ObjectMapper();

            try (InputStream stream = filePath.read()) {
                CheckRunOutput newOutput = mapper.readValue(stream, CheckRunOutput.class);
                output.merge(newOutput);
            }
        }
        if (title != null && !title.isEmpty()) {
            output.title = title;
        }
        if (summary != null && !summary.isEmpty()) {
            output.summary = summary;
        }
        if (text != null && !text.isEmpty()) {
            output.text = text;
        }

        if (action == null) {
            action = new CheckRunOutputAction(output);
            run.addAction(action);
        }

        if (flush) {
            CheckRunHelper.flush(run, listener);
        }
    }

    @Symbol("setCheckRunOutput")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.CreateCheckRunBuilder_DescriptorImpl_DisplayName();
        }
    }
}
