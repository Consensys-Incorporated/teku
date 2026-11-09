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

package tech.pegasys.teku.validator.client.restapi;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

class ValidatorRestApiConfigTest {

  @Test
  void validatorRestApiCanBeDisabled() {
    final ValidatorRestApiConfig config = ValidatorRestApiConfig.builder().build();
    assertThat(config).isInstanceOf(ValidatorRestApiConfig.class);
    assertThat(config.isRestApiEnabled()).isFalse();
  }

  @Test
  void validatorApiRequiresSsl() {
    assertThatThrownBy(() -> ValidatorRestApiConfig.builder().restApiEnabled(true).build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("no SSL keystore was provided");
  }

  @Test
  void validatorApiShouldNotLoadCertInHttpMode(@TempDir final Path tempPath) {
    final ValidatorRestApiConfig config =
        ValidatorRestApiConfig.builder()
            .restApiEnabled(true)
            .restApiSslEnabled(false)
            .validatorApiKeystoreFile(tempPath.resolve("keystore").toString())
            .validatorApiKeystorePasswordFile(tempPath.resolve("pass").toString())
            .build();
    assertThat(config.getRestApiKeystoreFile()).isEmpty();
    assertThat(config.getRestApiKeystorePasswordFile()).isEmpty();
  }

  @Test
  void validatorApiShouldAllowDisableSslForLocalhost() {
    final ValidatorRestApiConfig config =
        ValidatorRestApiConfig.builder().restApiEnabled(true).restApiSslEnabled(false).build();
    assertThat(config.isRestApiEnabled()).isTrue();
    assertThat(config.getRestApiKeystoreFile()).isEmpty();
  }

  @Test
  void validatorApiShouldNotAllowDisableSslForMostHosts() {
    assertThatThrownBy(
            () ->
                ValidatorRestApiConfig.builder()
                    .restApiEnabled(true)
                    .restApiSslEnabled(false)
                    .restApiInterface("192.168.1.1")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SSL connections can only be disabled on the localhost interface");
  }

  @Test
  void validatorApiRequiresKeystoreToExist(@TempDir final Path tempPath) {
    assertThatThrownBy(
            () ->
                ValidatorRestApiConfig.builder()
                    .restApiEnabled(true)
                    .validatorApiKeystoreFile(tempPath.resolve("keystore").toString())
                    .validatorApiKeystorePasswordFile(tempPath.resolve("pass").toString())
                    .build())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("Could not access Validator api keystore");
  }

  @Test
  void validatorApiDoesNotRequiresPasswordToExist(@TempDir final Path tempPath) throws IOException {
    assertThat(tempPath.resolve("keystore").toFile().createNewFile()).isTrue();
    final ValidatorRestApiConfig config =
        ValidatorRestApiConfig.builder()
            .restApiEnabled(true)
            .validatorApiKeystoreFile(tempPath.resolve("keystore").toString())
            .build();
    assertThat(config).isInstanceOf(ValidatorRestApiConfig.class);
    assertThat(config.isRestApiEnabled()).isTrue();
  }
}
