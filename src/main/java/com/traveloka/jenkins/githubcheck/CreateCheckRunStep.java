package com.traveloka.jenkins.githubcheck;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jenkinsci.Symbol;
import org.kohsuke.github.GHCheckRun.Conclusion;
import org.kohsuke.github.GHCheckRun.Status;
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

public class CreateCheckRunStep extends Builder implements SimpleBuildStep {

    private final String name;
    private final String status;
    private final String conclusion;
    private String outputFile;
    private String title;
    private String summary;

    @DataBoundConstructor
    public CreateCheckRunStep(String name, String status, String conclusion) {
        this.name = name;
        this.status = status;
        this.conclusion = conclusion;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public String getConclusion() {
        return conclusion;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    @DataBoundSetter
    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    @DataBoundSetter
    public void setTitle(String title) {
        this.title = title;
    }

    @DataBoundSetter
    public void setSummary(String summary) {
        this.summary = summary;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {

        CheckRunOutput output = new CheckRunOutput();
        if (outputFile != null && !outputFile.isEmpty()) {
            FilePath file = workspace.child(outputFile);
            ObjectMapper mapper = new ObjectMapper();
            try (InputStream stream = file.read()) {
                output = mapper.readValue(stream, CheckRunOutput.class);
            }
        }
        Status crStatus = Status.IN_PROGRESS;
        Conclusion crConclusion = Conclusion.NEUTRAL;
        if (status != null && !status.isEmpty()) {
            crStatus = Status.valueOf(status.toUpperCase());
        }
        if (conclusion != null && !conclusion.isEmpty()) {
            crConclusion = Conclusion.valueOf(conclusion.toUpperCase());
        }
        if (title != null && !title.isEmpty()) {
            output.title = title;
        }
        if (summary != null && !summary.isEmpty()) {
            output.summary = summary;
        }
        CheckRunHelper cr = new CheckRunHelper(run, listener);
        cr.create(name, crStatus, crConclusion, output);
    }

    @Symbol("createCheckRun")
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
