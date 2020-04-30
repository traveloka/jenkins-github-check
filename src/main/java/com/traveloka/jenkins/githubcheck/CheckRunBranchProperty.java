package com.traveloka.jenkins.githubcheck;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.JobDecorator;
import org.kohsuke.stapler.DataBoundConstructor;

public class CheckRunBranchProperty extends BranchProperty {
    /**
     * The name of the check run
     */
    private final String checkName;

    /**
     * Constructor.
     * 
     * @param checkName The name of the check run
     */
    @DataBoundConstructor
    public CheckRunBranchProperty(String checkName) {
        this.checkName = checkName;
    }

    /**
     * The name of the check run
     * 
     * @return The name of the check run
     */
    public String getCheckName() {
        return checkName;
    }

    @Override
    public <P extends Job<P, B>, B extends Run<P, B>> JobDecorator<P, B> jobDecorator(Class<P> clazz) {
        return null;
    }

    @Extension
    public static class DescriptorImpl extends BranchPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.GithubCheckBranchProperty_DescriptorImpl_DisplayName();
        }

    }
}
