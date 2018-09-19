/*
 * The MIT License
 *
 *  Copyright (c) 2016, CloudBees, Inc.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */

package com.cloudbees.jenkins.plugins.amazonecs.credentials;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSSessionCredentials;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSCredentialsBinding extends MultiBinding<ECSCredentials> {

    private static final String DEFAULT_ACCESS_KEY_ID_VARIABLE_NAME = "AWS_ACCESS_KEY_ID";
    private static final String DEFAULT_SECRET_ACCESS_KEY_VARIABLE_NAME = "AWS_SECRET_ACCESS_KEY";
    private static final String SESSION_TOKEN_VARIABLE_NAME = "AWS_SESSION_TOKEN";

    private final String accessKeyVariable;
    private final String secretKeyVariable;

    /**
     *
     * @param accessKeyVariable if {@code null}, {@value DEFAULT_ACCESS_KEY_ID_VARIABLE_NAME} will be used.
     * @param secretKeyVariable if {@code null}, {@value DEFAULT_SECRET_ACCESS_KEY_VARIABLE_NAME} will be used.
     * @param credentialsId  Identifier of the credentials to be bound.
     */
    @DataBoundConstructor
    public ECSCredentialsBinding(String accessKeyVariable, String secretKeyVariable, String credentialsId) {
        super(credentialsId);
        this.accessKeyVariable = StringUtils.defaultIfBlank(accessKeyVariable, DEFAULT_ACCESS_KEY_ID_VARIABLE_NAME);
        this.secretKeyVariable = StringUtils.defaultIfBlank(secretKeyVariable, DEFAULT_SECRET_ACCESS_KEY_VARIABLE_NAME);
    }

    public String getAccessKeyVariable() {
        return accessKeyVariable;
    }

    public String getSecretKeyVariable() {
        return secretKeyVariable;
    }

    @Override
    protected Class<ECSCredentials> type() {
        return ECSCredentials.class;
    }

    @Override
    public MultiEnvironment bind(@Nonnull Run<?, ?> build, FilePath workspace, Launcher launcher, @Nonnull TaskListener listener) throws IOException {
        AWSCredentials credentials = getCredentials(build).getCredentials();
        Map<String,String> m = new HashMap<>();
        m.put(accessKeyVariable, credentials.getAWSAccessKeyId());
        m.put(secretKeyVariable, credentials.getAWSSecretKey());

        // If role has been assumed, STS requires AWS_SESSION_TOKEN variable set too.
        if(credentials instanceof AWSSessionCredentials) {
            m.put(SESSION_TOKEN_VARIABLE_NAME, ((AWSSessionCredentials) credentials).getSessionToken());
        }
        return new MultiEnvironment(m);
    }

    @Override
    public Set<String> variables() {
        return new HashSet<>(Arrays.asList(accessKeyVariable, secretKeyVariable));
    }

    @Extension
    public static class DescriptorImpl extends BindingDescriptor<ECSCredentials> {

        @Override protected Class<ECSCredentials> type() {
            return ECSCredentials.class;
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "AWS access key and secret";
        }
    }

}
