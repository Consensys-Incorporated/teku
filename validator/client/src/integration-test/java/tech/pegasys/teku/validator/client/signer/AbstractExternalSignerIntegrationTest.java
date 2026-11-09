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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueueWithPriority;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.loader.HttpClientExternalSignerFactory;

@ExtendWith(MockServerExtension.class)
public abstract class AbstractExternalSignerIntegrationTest {

  protected static final Duration TIMEOUT = Duration.ofMillis(500);
  protected static final BLSKeyPair KEYPAIR = BLSTestUtil.randomKeyPair(1234);

  protected final Spec spec = getSpec();
  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  protected final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  protected final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final ThrottlingTaskQueueWithPriority queue =
      ThrottlingTaskQueueWithPriority.create(
          8,
          DEFAULT_MAXIMUM_QUEUE_SIZE,
          metricsSystem,
          TekuMetricCategory.VALIDATOR,
          "externalSignerTest",
          "externalSignerTestRejected");
  protected final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);

  protected ClientAndServer client;
  protected ExternalSigner externalSigner;

  @BeforeEach
  void setup(final ClientAndServer client) throws MalformedURLException {
    this.client = client;
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerPublicKeySources(List.of(KEYPAIR.getPublicKey().toString()))
            .validatorExternalSignerUrl(
                URI.create("http://127.0.0.1:" + client.getLocalPort()).toURL())
            .validatorExternalSignerTimeout(TIMEOUT)
            .build();
    final Supplier<HttpClient> externalSignerHttpClientFactory =
        HttpClientExternalSignerFactory.create(config);

    externalSigner =
        new ExternalSigner(
            spec,
            externalSignerHttpClientFactory.get(),
            config.getValidatorExternalSignerUrl(),
            KEYPAIR.getPublicKey(),
            TIMEOUT,
            queue,
            metricsSystem);
  }

  @AfterEach
  void tearDown() {
    client.reset();
  }

  public abstract Spec getSpec();
}
