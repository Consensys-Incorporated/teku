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

package tech.pegasys.teku.validator.client.loader;

import static java.nio.file.Files.createTempFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue.DEFAULT_MAXIMUM_QUEUE_SIZE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.google.common.base.Charsets;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import tech.pegasys.techu.service.serviceutils.layout.SimpleDataDirLayout;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueueWithPriority;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.ValidatorClientService;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeletionStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ImportStatus;

public class ExternalValidatorSourceTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final PublicKeyLoader publicKeyLoader = mock(PublicKeyLoader.class);
  private final HttpClient httpClient = mock(HttpClient.class);
  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private final AsyncRunner asyncRunner = new StubAsyncRunner();
  private ThrottlingTaskQueueWithPriority externalSignerTaskQueue;

  private final Supplier<HttpClient> httpClientFactory = () -> httpClient;

  @SuppressWarnings("unchecked")
  private final HttpResponse<Void> httpResponse = mock(HttpResponse.class);

  private ValidatorConfig config;
  private ValidatorSource validatorSource;

  public ExternalValidatorSourceTest() {}

  @BeforeEach
  void setup(@TempDir final Path tempDir) throws IOException, InterruptedException {
    config =
        ValidatorConfig.builder()
            .validatorExternalSignerUrl(URI.create("http://localhost:9000").toURL())
            .build();
    externalSignerTaskQueue =
        ThrottlingTaskQueueWithPriority.create(
            config.getValidatorExternalSignerConcurrentRequestLimit(),
            DEFAULT_MAXIMUM_QUEUE_SIZE,
            metricsSystem,
            TekuMetricCategory.VALIDATOR,
            "external_signer_request_queue_size",
            "external_signer_request_queue_rejected_total");
    when(httpResponse.statusCode()).thenReturn(SC_OK);
    when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
        .thenReturn(httpResponse);
    validatorSource = newExternalValidatorSource(tempDir, true);
  }

  @Test
  void shouldThrowExceptionWhenAddValidator() {
    assertThatThrownBy(() -> validatorSource.addValidator(null, "pass", BLSPublicKey.empty()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldReturnErrorWhenDeleteReadOnlyValidator() {
    assertThat(validatorSource.deleteValidator(BLSPublicKey.empty()))
        .isEqualTo(
            DeleteKeyResult.error(
                "Cannot delete validator from read-only external validator source."));
  }

  @Test
  void shouldDeleteValidator(@TempDir final Path tempDir) throws IOException {
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    final ExternalValidatorSource externalValidatorSource =
        newExternalValidatorSource(tempDir, false);

    final AddValidatorResult addValidatorResult =
        externalValidatorSource.addValidator(publicKey, Optional.empty());
    assertImportedSuccessfully(addValidatorResult, URI.create("http://localhost:9000").toURL());
    assertFileContent(tempDir, publicKey, "{\"pubkey\":\"" + publicKey + "\"}");

    final DeleteKeyResult result = externalValidatorSource.deleteValidator(publicKey);
    assertThat(result.getStatus()).isEqualTo(DeletionStatus.DELETED);
    assertThat(result.getMessage()).isEqualTo(Optional.empty());

    final Path path =
        ValidatorClientService.getManagedRemoteKeyPath(new SimpleDataDirLayout(tempDir))
            .resolve(publicKey.toBytesCompressed().toUnprefixedHexString() + ".json");
    assertThat(path.toFile()).doesNotExist();
  }

  @Test
  void shouldReturnErrorWhenDeleteNonExistentValidator(@TempDir final Path tempDir) {
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    final ExternalValidatorSource externalValidatorSource =
        newExternalValidatorSource(tempDir, false);

    final DeleteKeyResult result = externalValidatorSource.deleteValidator(publicKey);
    assertThat(result.getStatus()).isEqualTo(DeletionStatus.NOT_FOUND);
    assertThat(result.getMessage()).isEqualTo(Optional.empty());

    final Path path =
        ValidatorClientService.getManagedRemoteKeyPath(new SimpleDataDirLayout(tempDir))
            .resolve(publicKey.toBytesCompressed().toUnprefixedHexString() + ".json");
    assertThat(path.toFile()).doesNotExist();
  }

  @Test
  void shouldSayFalseToAddValidators() {
    assertThat(validatorSource.canUpdateValidators()).isFalse();
  }

  @Test
  void addValidator_shouldGetConfigUrlWhenNotProvided(@TempDir final Path tempDir)
      throws IOException {
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    final ExternalValidatorSource externalValidatorSource =
        newExternalValidatorSource(tempDir, false);
    final AddValidatorResult result =
        externalValidatorSource.addValidator(publicKey, Optional.empty());
    assertImportedSuccessfully(result, config.getValidatorExternalSignerUrl());

    assertFileContent(tempDir, publicKey, "{\"pubkey\":\"" + publicKey + "\"}");
  }

  @Test
  void addValidator_shouldGetUrlIfProvided(@TempDir final Path tempDir) throws IOException {
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    final URL url = URI.create("http://host.com").toURL();

    final ExternalValidatorSource externalValidatorSource =
        newExternalValidatorSource(tempDir, false);
    final AddValidatorResult result =
        externalValidatorSource.addValidator(publicKey, Optional.of(url));
    assertImportedSuccessfully(result, url);

    assertFileContent(
        tempDir, publicKey, "{\"pubkey\":\"" + publicKey + "\",\"url\":\"http://host.com\"}");
  }

  @Test
  void addValidator_shouldDetectDuplicate(@TempDir final Path tempDir) throws IOException {
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();

    final ExternalValidatorSource externalValidatorSource =
        newExternalValidatorSource(tempDir, false);
    final AddValidatorResult result1 =
        externalValidatorSource.addValidator(publicKey, Optional.empty());
    assertImportedSuccessfully(result1, config.getValidatorExternalSignerUrl());

    final AddValidatorResult result2 =
        externalValidatorSource.addValidator(publicKey, Optional.empty());
    assertThat(result2.getResult().getImportStatus()).isEqualTo(ImportStatus.DUPLICATE);
    assertThat(result2.getSigner()).isEmpty();

    assertFileContent(tempDir, publicKey, "{\"pubkey\":\"" + publicKey + "\"}");
  }

  @Test
  void shouldLoadExternalValidators(@TempDir final Path tempDir) throws IOException {
    final DataDirLayout dataDirLayout = new SimpleDataDirLayout(tempDir);
    final ExternalValidatorSource externalValidatorSource =
        newExternalValidatorSource(tempDir, false);

    final BLSPublicKey publicKey1 = dataStructureUtil.randomPublicKey();
    createRemoteKeyFile(
        dataDirLayout, publicKey1, Optional.of(URI.create("http://host.com").toURL()));

    final BLSPublicKey publicKey2 = dataStructureUtil.randomPublicKey();
    createRemoteKeyFile(dataDirLayout, publicKey2, Optional.empty());

    final List<? extends ValidatorSource.ValidatorProvider> validators =
        externalValidatorSource.getAvailableValidators();

    final List<ValidatorProviderInfo> result =
        validators.stream()
            .map(
                v -> {
                  assertThat(v).isInstanceOf(ExternalValidatorProvider.class);
                  ExternalValidatorProvider provider = (ExternalValidatorProvider) v;
                  return new ValidatorProviderInfo(
                      provider.getPublicKey(), provider.getExternalSignerUrl());
                })
            .collect(Collectors.toList());

    assertThat(result)
        .containsExactlyInAnyOrder(
            new ValidatorProviderInfo(publicKey1, URI.create("http://host.com").toURL()),
            new ValidatorProviderInfo(publicKey2, URI.create("http://localhost:9000").toURL()));
  }

  @Test
  void shouldGetAvailableReadOnlyValidators(@TempDir final Path tempDir) throws IOException {
    final BLSPublicKey publicKey1 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey publicKey2 = dataStructureUtil.randomPublicKey();
    when(publicKeyLoader.getPublicKeys(any())).thenReturn(List.of(publicKey1, publicKey2));

    final ExternalValidatorSource externalValidatorSource =
        newExternalValidatorSource(tempDir, true);
    final List<? extends ValidatorSource.ValidatorProvider> validators =
        externalValidatorSource.getAvailableValidators();
    final List<ValidatorProviderInfo> result =
        validators.stream()
            .map(
                validatorProvider -> {
                  assertThat(validatorProvider).isInstanceOf(ExternalValidatorProvider.class);
                  final ExternalValidatorProvider provider =
                      (ExternalValidatorProvider) validatorProvider;
                  return new ValidatorProviderInfo(
                      provider.getPublicKey(), provider.getExternalSignerUrl());
                })
            .collect(Collectors.toList());

    assertThat(result)
        .containsExactlyInAnyOrder(
            new ValidatorProviderInfo(publicKey1, URI.create("http://localhost:9000").toURL()),
            new ValidatorProviderInfo(publicKey2, URI.create("http://localhost:9000").toURL()));
  }

  private void createRemoteKeyFile(
      final DataDirLayout dataDirLayout, final BLSPublicKey publicKey, final Optional<URL> url)
      throws IOException {
    final String urlContent = url.map(value -> ",\"url\":\"" + value + "\"").orElse("");
    final String content = "{\"pubkey\":\"" + publicKey + "\"" + urlContent + "}";

    final Path directory = ValidatorClientService.getManagedRemoteKeyPath(dataDirLayout);
    directory.toFile().mkdirs();

    final Path tempFile =
        createTempFile(directory, publicKey.toBytesCompressed().toUnprefixedHexString(), ".json");
    Files.writeString(tempFile, content, StandardCharsets.UTF_8);
  }

  private void assertFileContent(
      final Path tempDir, final BLSPublicKey publicKey, final String expectedContent)
      throws IOException {
    final String fileName = publicKey.toBytesCompressed().toUnprefixedHexString() + ".json";
    final Path path =
        ValidatorClientService.getManagedRemoteKeyPath(new SimpleDataDirLayout(tempDir))
            .resolve(fileName);
    assertThat(path.toFile()).exists();
    assertThat(Files.readString(path, Charsets.UTF_8)).isEqualTo(expectedContent);
  }

  private void assertImportedSuccessfully(final AddValidatorResult result, final URL expectedUrl) {
    assertThat(result.getResult().getImportStatus()).isEqualTo(ImportStatus.IMPORTED);
    assertThat(result.getSigner()).isNotEmpty();
    assertThat(result.getSigner().get().getSigningServiceUrl()).isNotEmpty();
    assertThat(result.getSigner().get().getSigningServiceUrl().get()).isEqualTo(expectedUrl);
  }

  private ExternalValidatorSource newExternalValidatorSource(
      final Path tempDir, final boolean readOnly) {
    return ExternalValidatorSource.create(
        spec,
        metricsSystem,
        config,
        httpClientFactory,
        publicKeyLoader,
        asyncRunner,
        readOnly,
        externalSignerTaskQueue,
        Optional.of(new SimpleDataDirLayout(tempDir)));
  }

  static class ValidatorProviderInfo {
    private final BLSPublicKey publicKey;
    private final URL url;

    ValidatorProviderInfo(final BLSPublicKey publicKey, final URL url) {
      this.publicKey = publicKey;
      this.url = url;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ValidatorProviderInfo that = (ValidatorProviderInfo) o;
      return Objects.equals(publicKey, that.publicKey) && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
      return Objects.hash(publicKey, url);
    }

    @Override
    public String toString() {
      return "ValidatorProviderInfo{" + "publicKey=" + publicKey + ", url=" + url + '}';
    }
  }
}
