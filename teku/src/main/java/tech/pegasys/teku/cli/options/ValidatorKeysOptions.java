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

import com.google.common.base.Strings;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.validator.api.ValidatorConfig;

public class ValidatorKeysOptions {

  @CommandLine.Option(
      names = {"--validator-keys"},
      paramLabel = "<KEY_DIR>:<PASS_DIR> | <KEY_FILE>:<PASS_FILE> | <KEY_DIR>:<PASS_FILE>",
      description =
          "<KEY_DIR>:<PASS_DIR> will find <KEY_DIR>/**.json, and expect to find <PASS_DIR>/**.txt.\n"
              + "<KEY_FILE>:<PASS_FILE> will expect that the file <KEY_FILE> exists, "
              + "and the file containing the password for it is <PASS_FILE>.\n"
              + "<KEY_DIR>:<PASS_FILE> will find <KEY_DIR>/**.json, "
              + "and use <PASS_FILE> as the password for all keystores.",
      split = ",",
      arity = "1..*")
  private List<String> validatorKeys = new ArrayList<>();

  @CommandLine.Option(
      names = {"--validators-external-signer-public-keys"},
      paramLabel = "<STRINGS>",
      description = "The list of external signer public keys, or a URL to load the keys from",
      split = ",",
      arity = "0..*")
  private List<String> validatorExternalSignerPublicKeys = new ArrayList<>();

  @CommandLine.Option(
      names = {"--validators-external-signer-url"},
      paramLabel = "<NETWORK>",
      description = "URL for the external signing service",
      arity = "1")
  private String validatorExternalSignerUrl = null;

  @CommandLine.Option(
      names = {"--validators-external-signer-timeout"},
      paramLabel = "<INTEGER>",
      description = "Timeout (in milliseconds) for the external signing service",
      arity = "1")
  private long validatorExternalSignerTimeout =
      ValidatorConfig.DEFAULT_VALIDATOR_EXTERNAL_SIGNER_TIMEOUT.toMillis();

  @CommandLine.Option(
      names = {"--validators-external-signer-keystore"},
      paramLabel = "<FILE>",
      description =
          "Keystore (PKCS12/JKS) to use for TLS mutual authentication with external signer",
      arity = "1")
  private String validatorExternalSignerKeystore = null;

  @CommandLine.Option(
      names = {"--validators-external-signer-keystore-password-file"},
      paramLabel = "<FILE>",
      description =
          "Password file to decrypt keystore (PKCS12/JKS) that will be used for TLS mutual authentication with external signer",
      arity = "1")
  private String validatorExternalSignerKeystorePasswordFile = null;

  @CommandLine.Option(
      names = {"--validators-external-signer-truststore"},
      paramLabel = "<FILE>",
      description = "Keystore (PKCS12/JKS) to trust external signer's self-signed certificate",
      arity = "1")
  private String validatorExternalSignerTruststore = null;

  @CommandLine.Option(
      names = {"--validators-external-signer-truststore-password-file"},
      paramLabel = "<FILE>",
      description =
          "Password file to decrypt keystore (PKCS12/JKS) that will be used to trust external signer's self-signed certificate",
      arity = "1")
  private String validatorExternalSignerTruststorePasswordFile = null;

  @CommandLine.Option(
      names = {"--validators-external-signer-concurrent-limit"},
      paramLabel = "<INTEGER>",
      description =
          "The maximum number of concurrent background requests to make to the signer, must be between 1 and 1024 inclusive. This only applies for aggregation slot and validator registration requests.",
      arity = "1")
  private int validatorExternalSignerConcurrentRequestLimit =
      ValidatorConfig.DEFAULT_VALIDATOR_EXTERNAL_SIGNER_CONCURRENT_REQUEST_LIMIT;

  public void configure(final TekuConfiguration.Builder builder) {
    builder.validator(
        config ->
            config
                .validatorKeys(validatorKeys)
                .validatorExternalSignerPublicKeySources(parseValidatorExternalKeys())
                .validatorExternalSignerUrl(parseValidatorExternalSignerUrl())
                .validatorExternalSignerConcurrentRequestLimit(
                    validatorExternalSignerConcurrentRequestLimit)
                .validatorExternalSignerTimeout(Duration.ofMillis(validatorExternalSignerTimeout))
                .validatorExternalSignerKeystore(convertToPath(validatorExternalSignerKeystore))
                .validatorExternalSignerKeystorePasswordFile(
                    convertToPath(validatorExternalSignerKeystorePasswordFile))
                .validatorExternalSignerTruststore(convertToPath(validatorExternalSignerTruststore))
                .validatorExternalSignerTruststorePasswordFile(
                    convertToPath(validatorExternalSignerTruststorePasswordFile)));
  }

  private List<String> parseValidatorExternalKeys() {
    return validatorExternalSignerPublicKeys.stream().filter(StringUtils::isNotBlank).toList();
  }

  private URL parseValidatorExternalSignerUrl() {
    if (Strings.isNullOrEmpty(validatorExternalSignerUrl)) {
      return null;
    }
    try {
      return URI.create(validatorExternalSignerUrl).toURL();
    } catch (IllegalArgumentException | MalformedURLException e) {
      throw new InvalidConfigurationException(
          "Invalid configuration. Signer URL has invalid syntax", e);
    }
  }

  private Path convertToPath(final String option) {
    if (Strings.isNullOrEmpty(option)) {
      return null;
    }
    return Path.of(option);
  }
}
