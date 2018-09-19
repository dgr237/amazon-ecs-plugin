package com.cloudbees.jenkins.plugins.amazonecs.credentials;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.Collections;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ECSCredentialsHelper {

    private ECSCredentialsHelper() {}

    @CheckForNull
    public static ECSCredentials getCredentials(@Nullable String credentialsId, ItemGroup context) {
        if (StringUtils.isBlank(credentialsId)) {
            return null;
        }
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(ECSCredentials.class, context,
                        ACL.SYSTEM, Collections.emptyList()),
                CredentialsMatchers.withId(credentialsId));
    }

    public static ListBoxModel doFillCredentialsIdItems(ItemGroup context) {
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(ACL.SYSTEM, context, ECSCredentials.class, Collections.emptyList(), CredentialsMatchers.always());
    }
}
