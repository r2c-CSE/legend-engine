// Copyright 2021 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.authentication.vault.impl;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.FixedSizeList;
import org.finos.legend.authentication.vault.CredentialVault;
import org.finos.legend.authentication.vault.PlatformCredentialVaultProvider;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.authentication.vault.CredentialVaultSecret;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.authentication.vault.EnvironmentCredentialVaultSecret;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.authentication.vault.PropertiesFileSecret;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.authentication.vault.SystemPropertiesSecret;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.authentication.vault.aws.AWSCredentials;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.authentication.vault.aws.AWSDefaultCredentials;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.authentication.vault.aws.AWSSecretsManagerSecret;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.authentication.vault.aws.StaticAWSCredentials;
import org.finos.legend.engine.shared.core.identity.Identity;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

public class AWSSecretsManagerVault extends CredentialVault<AWSSecretsManagerSecret>
{
    private PlatformCredentialVaultProvider platformCredentialVaultProvider;

    public static Builder builder()
    {
        return new Builder();
    }

    public AWSSecretsManagerVault(PlatformCredentialVaultProvider platformCredentialVaultProvider)
    {
        this.platformCredentialVaultProvider = platformCredentialVaultProvider;
    }

    @Override
    public String lookupSecret(AWSSecretsManagerSecret vaultSecret, Identity identity) throws Exception
    {
        SecretsManagerClient secretsManagerClient = SecretsManagerClient.builder()
                .credentialsProvider(this.configureStsClient(vaultSecret.awsCredentials))
                .region(Region.US_EAST_1)
                .build();

        GetSecretValueRequest.Builder requestBuilder = GetSecretValueRequest.builder()
                .secretId(vaultSecret.secretId);

        if (vaultSecret.versionId != null)
        {
            requestBuilder.versionId(vaultSecret.versionId);
        }
        if (vaultSecret.versionStage != null)
        {
            requestBuilder.versionStage(vaultSecret.versionStage);
        }

        GetSecretValueRequest getSecretValueRequest = requestBuilder.build();
        GetSecretValueResponse secretValue = secretsManagerClient.getSecretValue(getSecretValueRequest);
        return secretValue.secretString();
    }

    private AwsCredentialsProvider configureStsClient(AWSCredentials awsCredentials) throws Exception
    {
        if (awsCredentials instanceof AWSDefaultCredentials)
        {
            return DefaultCredentialsProvider.builder().build();
        }

        if (awsCredentials instanceof StaticAWSCredentials)
        {
            StaticAWSCredentials staticAWSCredentials  = (StaticAWSCredentials)awsCredentials;
            CredentialVaultSecret accessKeyIdSecret = validate(staticAWSCredentials.accessKeyId);
            CredentialVaultSecret secretAccessKeySecret = validate(staticAWSCredentials.secretAccessKey);

            String accessKeyIdValue = this.platformCredentialVaultProvider.getVault(accessKeyIdSecret).lookupSecret(accessKeyIdSecret, null);
            String secretAccessKeyValue = this.platformCredentialVaultProvider.getVault(accessKeyIdSecret).lookupSecret(secretAccessKeySecret, null);
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyIdValue, secretAccessKeyValue));
        }

        throw new UnsupportedOperationException("Unsupported AWSCredentials of type " + awsCredentials.getClass().getCanonicalName());
    }

    private static FixedSizeList<Class<? extends CredentialVaultSecret>> SUPPORTED_TYPES = Lists.fixedSize.of(PropertiesFileSecret.class, SystemPropertiesSecret.class, EnvironmentCredentialVaultSecret.class);

    private CredentialVaultSecret validate(CredentialVaultSecret secret)
    {
        if (!SUPPORTED_TYPES.contains(secret.getClass()))
        {
            throw new UnsupportedOperationException("Unsupported secret of type=" + secret.getClass().getCanonicalName() + ". Only supported type is=" + PropertiesFileSecret.class.getCanonicalName());
        }
        return secret;
    }

    public static class Builder
    {
        private PlatformCredentialVaultProvider platformCredentialVaultProvider;

        public Builder()
        {

        }

        public Builder with(PlatformCredentialVaultProvider platformCredentialVaultProvider)
        {
            this.platformCredentialVaultProvider = platformCredentialVaultProvider;
            return this;
        }

        public AWSSecretsManagerVault build()
        {
            return new AWSSecretsManagerVault(this.platformCredentialVaultProvider);
        }
    }
}
