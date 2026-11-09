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

package tech.pegasys.teku.validator.api;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

class ValidatorConfigTest {

  private final ValidatorConfig.Builder configBuilder = ValidatorConfig.builder();

  @Test
  public void shouldThrowIfExternalPublicKeysAreSpecifiedWithoutExternalSignerUrl() {
    final ValidatorConfig.Builder builder =
        configBuilder.validatorExternalSignerPublicKeySources(
            List.of(BLSTestUtil.randomKeyPair(0).getPublicKey().toString()));
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Invalid configuration. '--validators-external-signer-url' and '--validators-external-signer-public-keys' must be specified together");
  }

  @Test
  public void shouldNotThrowIfExternalSignerUrlIsSpecifiedWithoutExternalPublicKeys()
      throws MalformedURLException {
    final ValidatorConfig.Builder builder =
        configBuilder.validatorExternalSignerUrl(URI.create("http://localhost:9000").toURL());
    Assertions.assertThatCode(builder::build).doesNotThrowAnyException();
  }

  @Test
  public void shouldNotThrowIfBothExternalSignerUrlAndPublicKeysAreSpecified()
      throws MalformedURLException {
    final ValidatorConfig.Builder builder =
        configBuilder
            .validatorExternalSignerPublicKeySources(
                List.of(BLSTestUtil.randomKeyPair(0).getPublicKey().toString()))
            .validatorExternalSignerUrl(URI.create("http://localhost:9000").toURL());

    Assertions.assertThatCode(builder::build).doesNotThrowAnyException();
  }

  @Test
  public void shouldThrowIfExternalSignerKeystoreSpecifiedWithoutPasswordFile() {
    final ValidatorConfig.Builder builder =
        configBuilder.validatorExternalSignerKeystore(Path.of("somepath"));
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Invalid configuration. '--validators-external-signer-keystore' and '--validators-external-signer-keystore-password-file' must be specified together");
  }

  @Test
  public void shouldLogWarnIfExternalPublicKeysContainsURLReferringToExternalSignerUrlHost()
      throws MalformedURLException {

    try (final LogCaptor logCaptor = LogCaptor.forClass(ValidatorConfig.class)) {
      final ValidatorConfig.Builder builder =
          configBuilder
              .validatorExternalSignerPublicKeySources(List.of("http://localhost:9000/test"))
              .validatorExternalSignerUrl(URI.create("http://localhost:9000").toURL());

      Assertions.assertThatCode(builder::build).doesNotThrowAnyException();

      logCaptor.assertWarnLog(
          "'--validators-external-signer-public-keys' contains an URL matching the external-signer-url host and port. Use 'external-signer' instead if you want to use all public keys exposed by the external signer");
    }
  }

  @Test
  public void shouldNotThrowIfExternalPublicKeysContainsMalformedURLs()
      throws MalformedURLException {
    final ValidatorConfig.Builder builder =
        configBuilder
            .validatorExternalSignerPublicKeySources(List.of("invalid:url"))
            .validatorExternalSignerUrl(URI.create("http://localhost:9000").toURL());

    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Invalid configuration. '--validators-external-signer-public-keys' contains a malformed URL: invalid:url");
  }

  @Test
  public void shouldThrowIfExternalSignerKeystorePasswordFileIsSpecifiedWithoutKeystore() {
    final ValidatorConfig.Builder builder =
        configBuilder.validatorExternalSignerKeystorePasswordFile(Path.of("somepath"));
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Invalid configuration. '--validators-external-signer-keystore' and '--validators-external-signer-keystore-password-file' must be specified together");
  }

  @Test
  public void shouldNotThrowIfBothExternalSignerKeystoreAndPasswordFileAreSpecified() {
    final ValidatorConfig.Builder builder =
        configBuilder
            .validatorExternalSignerKeystore(Path.of("somepath"))
            .validatorExternalSignerKeystorePasswordFile(Path.of("somepath"));

    Assertions.assertThatCode(builder::build).doesNotThrowAnyException();
  }

  @Test
  public void shouldThrowIfExternalSignerTruststoreSpecifiedWithoutPasswordFile() {
    final ValidatorConfig.Builder builder =
        configBuilder.validatorExternalSignerTruststore(Path.of("somepath"));
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Invalid configuration. '--validators-external-signer-truststore' and '--validators-external-signer-truststore-password-file' must be specified together");
  }

  @Test
  public void shouldThrowIfExternalSignerTruststorePasswordFileIsSpecifiedWithoutTruststore() {
    final ValidatorConfig.Builder builder =
        configBuilder.validatorExternalSignerTruststorePasswordFile(Path.of("somepath"));
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Invalid configuration. '--validators-external-signer-truststore' and '--validators-external-signer-truststore-password-file' must be specified together");
  }

  @Test
  public void shouldThrowIfExternalSignerUrlHasUserInfoButTheSchemeIsNotHttps()
      throws MalformedURLException {
    final ValidatorConfig.Builder builder =
        configBuilder
            .validatorExternalSignerPublicKeySources(List.of("0x"))
            .validatorExternalSignerUrl(URI.create("http://foo:bar@localhost:1080").toURL());
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Invalid configuration. --validators-external-signer-url (http://localhost:1080) must start with https because external signer keystore/truststore are defined or basic authentication is used");
  }

  @Test
  public void shouldNotThrowIfBothExternalSignerTruststoreAndPasswordFileAreSpecified() {
    final ValidatorConfig.Builder builder =
        configBuilder
            .validatorExternalSignerTruststore(Path.of("somepath"))
            .validatorExternalSignerTruststorePasswordFile(Path.of("somepath"));

    Assertions.assertThatCode(builder::build).doesNotThrowAnyException();
  }

  @Test
  public void bellatrix_shouldThrowIfExternalSignerPublicKeySourcesIsSpecified()
      throws MalformedURLException {
    final ValidatorConfig config =
        configBuilder
            .validatorExternalSignerPublicKeySources(
                List.of(BLSTestUtil.randomKeyPair(0).getPublicKey().toString()))
            .validatorExternalSignerUrl(URI.create("http://localhost:9000").toURL())
            .build();

    verifyProposerConfigOrProposerDefaultFeeRecipientThrow(config);
  }

  @Test
  public void bellatrix_shouldThrowIfValidatorKeysAreSpecified() {
    final ValidatorConfig config = configBuilder.validatorKeys(List.of("some string")).build();

    verifyProposerConfigOrProposerDefaultFeeRecipientThrow(config);
  }

  @Test
  public void bellatrix_shouldNotThrowIfValidationIsActiveAndDefaultFeeRecipientIsSpecified()
      throws MalformedURLException {
    final ValidatorConfig config =
        configBuilder
            .validatorExternalSignerPublicKeySources(
                List.of(BLSTestUtil.randomKeyPair(0).getPublicKey().toString()))
            .validatorExternalSignerUrl(URI.create("http://localhost:9000").toURL())
            .proposerDefaultFeeRecipient("0x0000000000000000000000000000000000000000")
            .build();

    verifyProposerConfigOrProposerDefaultFeeRecipientNotThrow(config);
  }

  @Test
  public void bellatrix_shouldNotThrowIfValidationIsActiveAndProposerConfigSourceIsSpecified()
      throws MalformedURLException {
    final ValidatorConfig config =
        configBuilder
            .validatorExternalSignerPublicKeySources(
                List.of(BLSTestUtil.randomKeyPair(0).getPublicKey().toString()))
            .validatorExternalSignerUrl(URI.create("http://localhost:9000").toURL())
            .proposerConfigSource("some path")
            .build();

    verifyProposerConfigOrProposerDefaultFeeRecipientNotThrow(config);
  }

  void verifyProposerConfigOrProposerDefaultFeeRecipientNotThrow(final ValidatorConfig config) {
    Assertions.assertThatCode(config::getProposerDefaultFeeRecipient).doesNotThrowAnyException();
    Assertions.assertThatCode(config::getProposerConfigSource).doesNotThrowAnyException();
  }

  void verifyProposerConfigOrProposerDefaultFeeRecipientThrow(final ValidatorConfig config) {
    verifyProposerConfigOrProposerDefaultFeeRecipientThrow(config::getProposerDefaultFeeRecipient);
    verifyProposerConfigOrProposerDefaultFeeRecipientThrow(config::getProposerConfigSource);
  }

  void verifyProposerConfigOrProposerDefaultFeeRecipientThrow(final Runnable task) {
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(task::run)
        .withMessageContaining(
            "Invalid configuration. --validators-proposer-default-fee-recipient or --validators-proposer-config must be specified when Bellatrix milestone is active");
  }
}
