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

package tech.pegasys.teku.validator.client.signer;

import static tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue.DEFAULT_MAXIMUM_QUEUE_SIZE;

import com.google.common.io.Resources;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueueWithPriority;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.loader.HttpClientExternalSignerFactory;

@ExtendWith(MockServerExtension.class)
public abstract class AbstractSecureExternalSignerIntegrationTest {
  private static final BLSKeyPair KEYPAIR = BLSTestUtil.randomKeyPair(1234);
  private static final Duration TIMEOUT = Duration.ofMillis(500);
  private static final Path TEKU_KEYSTORE;
  private static final Path EXTERNAL_SIGNER_TRUSTSTORE;
  private static final Path PASSWORD_FILE;
  private static final Path TEKU_CLIENT_PEM;

  protected final Spec spec = TestSpecFactory.createMinimalAltair();
  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  protected final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final ThrottlingTaskQueueWithPriority queue =
      ThrottlingTaskQueueWithPriority.create(
          8,
          DEFAULT_MAXIMUM_QUEUE_SIZE,
          metricsSystem,
          TekuMetricCategory.VALIDATOR,
          "secureExternalSignerTest",
          "secureExternalSignerTestRejected");

  static {
    try {
      // MockServer (representing external signer) CA certificate is imported from:
      // https://github.com/mock-server/mockserver/blob/master/mockserver-core/src/main/resources/org/mockserver/socket/CertificateAuthorityCertificate.pem
      EXTERNAL_SIGNER_TRUSTSTORE = Path.of(Resources.getResource("mockito_truststore.p12").toURI());
      TEKU_KEYSTORE = Path.of(Resources.getResource("teku_client_keystore.p12").toURI());
      PASSWORD_FILE = Path.of(Resources.getResource("pass.txt").toURI());
      TEKU_CLIENT_PEM = Path.of(Resources.getResource("teku_client.pem").toURI());
    } catch (final URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  protected ClientAndServer client;
  protected ValidatorConfig validatorConfig;
  protected Supplier<HttpClient> externalSignerHttpClientFactory;
  protected ExternalSigner externalSigner;
  protected ExternalSignerUpcheck externalSignerUpcheck;

  @BeforeAll
  static void mTLSSetup() {
    // Mock Server configuration for mutual authentication
    ConfigurationProperties.tlsMutualAuthenticationRequired(true);
    ConfigurationProperties.tlsMutualAuthenticationCertificateChain(TEKU_CLIENT_PEM.toString());
  }

  @BeforeEach
  void setup(final ClientAndServer client) throws MalformedURLException {
    this.client = client;
    this.client.withSecure(true);

    validatorConfig =
        ValidatorConfig.builder()
            .validatorExternalSignerPublicKeySources(List.of(KEYPAIR.getPublicKey().toString()))
            .validatorExternalSignerUrl(getUrl())
            .validatorExternalSignerTimeout(TIMEOUT)
            .validatorExternalSignerKeystore(TEKU_KEYSTORE)
            .validatorExternalSignerKeystorePasswordFile(PASSWORD_FILE)
            .validatorExternalSignerTruststore(EXTERNAL_SIGNER_TRUSTSTORE)
            .validatorExternalSignerTruststorePasswordFile(PASSWORD_FILE)
            .build();

    externalSignerHttpClientFactory = HttpClientExternalSignerFactory.create(validatorConfig);

    externalSigner =
        new ExternalSigner(
            spec,
            externalSignerHttpClientFactory.get(),
            validatorConfig.getValidatorExternalSignerUrl(),
            KEYPAIR.getPublicKey(),
            TIMEOUT,
            queue,
            metricsSystem);

    externalSignerUpcheck =
        new ExternalSignerUpcheck(
            externalSignerHttpClientFactory.get(),
            validatorConfig.getValidatorExternalSignerUrl(),
            validatorConfig.getValidatorExternalSignerTimeout());
  }

  @AfterEach
  void tearDown() {
    client.reset();
  }

  protected URL getUrl() throws MalformedURLException {
    return URI.create("https://127.0.0.1:" + client.getLocalPort()).toURL();
  }
}
