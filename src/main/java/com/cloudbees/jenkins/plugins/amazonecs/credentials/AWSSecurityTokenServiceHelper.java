package com.cloudbees.jenkins.plugins.amazonecs.credentials;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetSessionTokenRequest;
import com.amazonaws.services.securitytoken.model.GetSessionTokenResult;
import com.cloudbees.jenkins.plugins.amazonecs.JenkinsWrapper;

class AWSSecurityTokenServiceHelper {
    private static final int STS_CREDENTIALS_DURATION_SECONDS = 3600;

    AWSCredentialsProvider provider;
    String iamRoleArn;

    AWSSecurityTokenServiceHelper(AWSCredentialsProvider provider, String iamRoleArn) {
        this.provider = provider;
        this.iamRoleArn=iamRoleArn;
    }

    static AWSSecurityTokenService getService(AWSCredentialsProvider credentialsProvider) {
        return AWSSecurityTokenServiceClientBuilder.standard().withCredentials(credentialsProvider).build();
    }

    AWSCredentialsProvider getCredentials() {
        AWSSecurityTokenService service = getService(provider);
        return new STSAssumeRoleSessionCredentialsProvider.Builder(iamRoleArn, JenkinsWrapper.getInstance().getDisplayName()).withStsClient(service).build();
    }

    AWSCredentialsProvider getCredentialsWithMFAToken(String iamMfaSerialNumber, String mfaToken)
    {
        AWSCredentialsProvider awsCredentialsProvider=getCredentials();
        AWSSecurityTokenService service = getService(awsCredentialsProvider);

        GetSessionTokenRequest tokenRequest = new GetSessionTokenRequest()
                .withSerialNumber(iamMfaSerialNumber)
                .withDurationSeconds(STS_CREDENTIALS_DURATION_SECONDS)
                .withTokenCode(mfaToken);
        GetSessionTokenResult result = service.getSessionToken(tokenRequest);

        return new AWSStaticCredentialsProvider(new BasicSessionCredentials(result.getCredentials().getAccessKeyId(), result.getCredentials().getSecretAccessKey(), result.getCredentials().getSessionToken()));
    }
}
