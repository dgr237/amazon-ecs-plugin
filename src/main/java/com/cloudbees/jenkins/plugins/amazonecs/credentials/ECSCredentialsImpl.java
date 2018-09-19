/*
 * The MIT License
 *
 *  Copyright (c) 2015, CloudBees, Inc.
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

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.*;
import com.cloudbees.jenkins.plugins.amazonecs.ECSService;
import com.cloudbees.jenkins.plugins.amazonecs.JenkinsWrapper;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.Extension;
import hudson.Util;
import hudson.util.FormValidation;
import hudson.util.Secret;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.net.HttpURLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ECSCredentialsImpl extends BaseECSCredentials implements ECSCredentials {

    private static final long serialVersionUID = -3167989896315282034L;

    private static final Logger LOGGER = Logger.getLogger(BaseECSCredentials.class.getName());

    private final String accessKey;

    private final Secret secretKey;

    private final String iamRoleArn;
    private final String iamMfaSerialNumber;

    /**
     * Old data bound constructor. It is maintained to keep binary compatibility with clients that were using it directly.
     */
    public ECSCredentialsImpl(CredentialsScope scope, String id,
                              String accessKey, String secretKey, String description) {
        this(scope, id, accessKey, secretKey, description, null, null);
    }

    @DataBoundConstructor
    public ECSCredentialsImpl(CredentialsScope scope, String id,
                              String accessKey, String secretKey, String description,
                              String iamRoleArn, String iamMfaSerialNumber) {
        super(scope, id, description);
        this.accessKey = Util.fixNull(accessKey);
        this.secretKey = Secret.fromString(secretKey);
        this.iamRoleArn = Util.fixNull(iamRoleArn);
        this.iamMfaSerialNumber = Util.fixNull(iamMfaSerialNumber);
    }

    public String getAccessKey() {
        return accessKey;
    }

    public Secret getSecretKey() {
        return secretKey;
    }

    public String getIamRoleArn() {
        return iamRoleArn;
    }

    public String getIamMfaSerialNumber() {
        return iamMfaSerialNumber;
    }

    public boolean requiresToken() {
        return !StringUtils.isBlank(iamMfaSerialNumber);
    }

    public AWSCredentials getCredentials() {
        return getCredentialsProvider(accessKey, secretKey, iamRoleArn, null, null).getCredentials();
    }

    private static AWSCredentialsProvider getCredentialsProvider(String accessKey,Secret secretKey, String iamRoleArn, String iamMfaSerialNumber, String mfaToken) {

        AWSCredentialsProvider provider = new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey.getPlainText()));

        if (!StringUtils.isBlank(iamRoleArn)) {
            // Handle the case of delegation to instance profile
            if (StringUtils.isBlank(accessKey) && StringUtils.isBlank(secretKey.getPlainText())) {
                provider = new InstanceProfileCredentialsProvider(false);
            }

            AWSSecurityTokenServiceHelper helper = new AWSSecurityTokenServiceHelper(provider, iamRoleArn);

            if (!StringUtils.isBlank(iamMfaSerialNumber) && !StringUtils.isBlank(mfaToken)) {
                return helper.getCredentialsWithMFAToken(iamMfaSerialNumber, mfaToken);
            } else {
                return helper.getCredentials();
            }
        }
        return provider;
    }

    public AWSCredentials getCredentials(String mfaToken) {
        return getCredentialsProvider(accessKey, secretKey, iamRoleArn, iamMfaSerialNumber, mfaToken).getCredentials();
    }

    public void refresh() {
        // no-op
    }

    public String getDisplayName() {
        if (StringUtils.isBlank(iamRoleArn)) {
            return accessKey;
        }
        return accessKey + ":" + iamRoleArn;
    }


    @Extension
    public static class DescriptorImpl extends CredentialsDescriptor {

        @Override
        @Nonnull
        public String getDisplayName() {
            return Messages.ECSCredentialsImpl_DisplayName();
        }

        public FormValidation doCheckSecretKey(@QueryParameter("accessKey") final String accessKey,
                                               @QueryParameter("iamRoleArn") final String iamRoleArn,
                                               @QueryParameter("iamMfaSerialNumber") final String iamMfaSerialNumber,
                                               @QueryParameter("iamMfaToken") final String iamMfaToken,
                                               @QueryParameter("regionName") final String regionName,
                                               @QueryParameter final String secretKey) {
            if (StringUtils.isBlank(accessKey) && StringUtils.isBlank(secretKey)) {
                return FormValidation.ok();
            }
            if (StringUtils.isBlank(accessKey)) {
                return FormValidation.error(Messages.ECSCredentialsImpl_SpecifyAccessKeyId());
            }
            if (StringUtils.isBlank(secretKey)) {
                return FormValidation.error(Messages.ECSCredentialsImpl_SpecifySecretAccessKey());
            }

            AWSCredentialsProvider awsCredentialsProvider;
            try {
                awsCredentialsProvider = ECSCredentialsImpl.getCredentialsProvider(accessKey, Secret.fromString(secretKey), iamRoleArn, iamMfaSerialNumber, iamMfaToken);

            }
            catch (AmazonServiceException e) {
                LOGGER.log(Level.WARNING, "Unable to assume role [" + iamRoleArn + "] ", e);
                return FormValidation.error(Messages.ECSCredentialsImpl_NotAbleToAssumeRole());
            }

            ECSService service=JenkinsWrapper.getECSService(awsCredentialsProvider,regionName);
            try {

                return FormValidation
                        .ok(Messages.ECSCredentialsImpl_CredentialsValidWithAccessToNClusters(
                                service.getClusterArns().size()));
            } catch (AmazonServiceException e) {
                if (HttpURLConnection.HTTP_UNAUTHORIZED == e.getStatusCode()) {
                    return FormValidation.warning(Messages.ECSCredentialsImpl_CredentialsInValid(e.getMessage()));
                } else if (HttpURLConnection.HTTP_FORBIDDEN == e.getStatusCode()) {
                    return FormValidation.ok(Messages.ECSCredentialsImpl_CredentialsValidWithoutAccessToECSInZone(regionName, e.getErrorMessage() + " (" + e.getErrorCode() + ")"));
                } else {
                    return FormValidation.error(e.getMessage());
                }
            } catch (AmazonClientException e) {
                return FormValidation.error(e.getMessage());
            }
        }

    }
}
