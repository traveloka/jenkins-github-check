package com.traveloka.jenkins.githubcheck;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

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

    private final String file;
    private final String title;
    private final String summary;
    private final String text;

    @DataBoundConstructor
    public SetCheckRunOutputStep(String file, String title, String summary, String text) {
        this.file = file;
        this.title = title;
        this.summary = summary;
        this.text = text;
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

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {

        CheckRunOutput output = new CheckRunOutput();
        if (file != null && file.isEmpty()) {
            FilePath filePath = workspace.child(file);
            listener.getLogger().printf("Using %s as github check output\n", filePath.toURI());
            ObjectMapper mapper = new ObjectMapper();

            try (InputStream stream = filePath.read()) {
                output = mapper.readValue(stream, CheckRunOutput.class);
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

        CheckRunOutputAction action = new CheckRunOutputAction(output);
        run.addAction(action);
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
