package com.cloudbees.jenkins.plugins.amazonecs.credentials;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.AssumedRoleUser;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.GetSessionTokenResult;
import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.Lookup;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.security.ConfidentialStore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.invoke.MethodHandles;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Secret.class,AWSSecurityTokenServiceHelper.class,Jenkins.class})
public class ECSCredentialsImplTest {

    @Test
    public void TestStandardCredentials()
    {
        Secret secret=mock(Secret.class);
        mockStatic(Secret.class);
        when(Secret.fromString(any())).thenReturn(secret);
        when(secret.getPlainText()).thenReturn("Secret");
        ECSCredentialsImpl credentialProvider=new ECSCredentialsImpl(CredentialsScope.GLOBAL,"Test","TestKey","SecretKey","AWS Credentials");
        AWSCredentials credentials=credentialProvider.getCredentials();
        Assert.assertEquals("TestKey", credentials.getAWSAccessKeyId());
        Assert.assertEquals("Secret",credentials.getAWSSecretKey());
    }

    @Test
    public void TestCredentialsWithIamRole() {
        AWSSecurityTokenService securityTokenService = mock(AWSSecurityTokenService.class);
        Secret secret = mock(Secret.class);
        mockStatic(Secret.class);
        mockStatic(AWSSecurityTokenServiceHelper.class);
        mockStatic(Jenkins.class);
        Jenkins jenkins = mock(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(jenkins);
        when(jenkins.getDisplayName()).thenReturn("Jenkins");
        when(AWSSecurityTokenServiceHelper.getService(any())).thenReturn(securityTokenService);
        when(Secret.fromString(any())).thenReturn(secret);
        when(secret.getPlainText()).thenReturn("Secret");
        when(securityTokenService.assumeRole(any())).thenReturn(new AssumeRoleResult().withCredentials(new Credentials().withAccessKeyId("TempKey").withSessionToken("token").withSecretAccessKey("TempSecret")).withAssumedRoleUser(new AssumedRoleUser().withArn("iamRole")));
        ECSCredentialsImpl credentialProvider = new ECSCredentialsImpl(CredentialsScope.GLOBAL, "Test", "TestKey", "SecretKey", "AWS Credentials", "iamRole", null);
        AWSCredentials credentials = credentialProvider.getCredentials();
        Assert.assertTrue(credentials instanceof BasicSessionCredentials);
        BasicSessionCredentials sessionCredentials = (BasicSessionCredentials) credentials;
        Assert.assertEquals("TempKey", sessionCredentials.getAWSAccessKeyId());
        Assert.assertEquals("TempSecret", sessionCredentials.getAWSSecretKey());
        Assert.assertEquals("token",sessionCredentials.getSessionToken());
    }

    @Test
    public void TestCredentialsWithIamRoleAndMfaToken() {
        AWSSecurityTokenService securityTokenService = mock(AWSSecurityTokenService.class);
        Secret secret = mock(Secret.class);
        mockStatic(Secret.class);
        mockStatic(AWSSecurityTokenServiceHelper.class);
        mockStatic(Jenkins.class);
        Jenkins jenkins = mock(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(jenkins);
        when(jenkins.getDisplayName()).thenReturn("Jenkins");
        when(AWSSecurityTokenServiceHelper.getService(any())).thenReturn(securityTokenService);
        when(Secret.fromString(any())).thenReturn(secret);
        when(secret.getPlainText()).thenReturn("Secret");
        when(securityTokenService.assumeRole(any())).thenReturn(new AssumeRoleResult().withCredentials(new Credentials().withAccessKeyId("TempKey").withSessionToken("token").withSecretAccessKey("TempSecret")).withAssumedRoleUser(new AssumedRoleUser().withArn("iamRole")));
        when(securityTokenService.getSessionToken(any())).thenReturn(new GetSessionTokenResult().withCredentials(new Credentials().withAccessKeyId("TempKey").withSessionToken("token").withSecretAccessKey("TempSecret")));
        ECSCredentialsImpl credentialProvider = new ECSCredentialsImpl(CredentialsScope.GLOBAL, "Test", "TestKey", "SecretKey", "AWS Credentials", "iamRole", "mfa-serial");
        AWSCredentials credentials = credentialProvider.getCredentials("mfa-token");
        Assert.assertTrue(credentials instanceof BasicSessionCredentials);
        BasicSessionCredentials sessionCredentials = (BasicSessionCredentials) credentials;
        Assert.assertEquals("TempKey", sessionCredentials.getAWSAccessKeyId());
        Assert.assertEquals("TempSecret", sessionCredentials.getAWSSecretKey());
        Assert.assertEquals("token",sessionCredentials.getSessionToken());
    }
}