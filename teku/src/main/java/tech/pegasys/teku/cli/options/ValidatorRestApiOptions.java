/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.cli.options;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.validator.client.restapi.ValidatorRestApiConfig;

public class ValidatorRestApiOptions {
  @CommandLine.Spec CommandLine.Model.CommandSpec cliSpec;

  @CommandLine.Option(
      names = {"--validator-api-port"},
      paramLabel = "<INTEGER>",
      description = "Port number of Rest API",
      arity = "1")
  private int restApiPort = ValidatorRestApiConfig.DEFAULT_REST_API_PORT;

  @CommandLine.Option(
      names = {"--validator-api-docs-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "Enable swagger-docs and swagger-ui endpoints",
      fallbackValue = "true",
      arity = "0..1")
  private boolean restApiDocsEnabled = false;

  @CommandLine.Option(
      names = {"--Xvalidator-api-ssl-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "Enable ssl for the validator-api. Can be disabled for localhost.",
      hidden = true,
      fallbackValue = "true",
      arity = "0..1")
  private boolean restApiSslEnabled = true;

  @CommandLine.Option(
      names = {"--Xvalidator-api-unsafe-hosts-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description =
          "Disable the sanity check on localhost only for non ssl connections. Very unsafe to send bearer on unencrypted connection on a network! Should only be used for testing.",
      hidden = true,
      fallbackValue = "true",
      arity = "0..1")
  private boolean validatorApiDisableSslHostRestrictionEnabled = false;

  @CommandLine.Option(
      names = {"--validator-api-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "Enables Validator Rest API",
      fallbackValue = "true",
      arity = "0..1")
  private boolean restApiEnabled = false;

  @CommandLine.Option(
      names = {"--validator-api-interface"},
      paramLabel = "<NETWORK>",
      description = "Interface of Validator Rest API",
      arity = "1")
  private String restApiInterface = ValidatorRestApiConfig.DEFAULT_REST_API_INTERFACE;

  @CommandLine.Option(
      names = {"--validator-api-bearer-file"},
      paramLabel = "<FILENAME>",
      description = "Use the specified file as the bearer token for the validator Rest API",
      arity = "1")
  private Path validatorApiBearerFile = null;

  @CommandLine.Option(
      names = {"--validator-api-host-allowlist"},
      paramLabel = "<hostname>",
      description = "Comma-separated list of hostnames to allow, or * to allow any host",
      split = ",",
      arity = "0..*")
  private final List<String> restApiHostAllowlist =
      ValidatorRestApiConfig.DEFAULT_REST_API_HOST_ALLOWLIST;

  @CommandLine.Option(
      names = {"--validator-api-cors-origins"},
      paramLabel = "<origin>",
      description = "Comma separated list of origins to allow, or * to allow any origin",
      split = ",",
      arity = "0..*")
  private final List<String> restApiCorsAllowedOrigins = new ArrayList<>();

  @CommandLine.Option(
      names = {"--validator-api-keystore-file"},
      paramLabel = "<keystoreFile>",
      description = "Keystore used for ssl for the validator api.",
      arity = "1")
  private String keystoreFile;

  @CommandLine.Option(
      names = {"--validator-api-keystore-password-file"},
      paramLabel = "<keystorePasswordFile>",
      description = "Password used to decrypt the keystore for the validator api.",
      arity = "1")
  private String keystorePasswordFile;

  public void configure(final TekuConfiguration.Builder builder) {
    builder.validatorApi(
        validatorApiBuilder ->
            validatorApiBuilder
                .restApiEnabled(restApiEnabled)
                .restApiDocsEnabled(restApiDocsEnabled)
                .restApiPort(restApiPort)
                .restApiInterface(restApiInterface)
                .restApiSslEnabled(restApiSslEnabled)
                .restApiCorsAllowedOrigins(restApiCorsAllowedOrigins)
                .validatorApiKeystoreFile(keystoreFile)
                .validatorApiKeystorePasswordFile(keystorePasswordFile)
                .validatorApiBearerFile(Optional.ofNullable(validatorApiBearerFile))
                .validatorApiDisableSslHostRestrictionEnabled(
                    validatorApiDisableSslHostRestrictionEnabled)
                .restApiHostAllowlist(restApiHostAllowlist));
  }
}
