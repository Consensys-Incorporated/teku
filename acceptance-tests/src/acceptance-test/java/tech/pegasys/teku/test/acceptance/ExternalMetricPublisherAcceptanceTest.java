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

package tech.pegasys.teku.test.acceptance;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.ExternalMetricNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class ExternalMetricPublisherAcceptanceTest extends AcceptanceTestBase {
  private static final int ACTIVE_VALIDATOR_COUNT = 8;
  private static final int TOTAL_VALIDATOR_COUNT = 64;

  @Test
  void shouldPublishDataFromPrometheus() throws Throwable {
    ExternalMetricNode externalMetricNode = createExternalMetricNode();
    externalMetricNode.start();

    final TekuBeaconNode tekuNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withExternalMetricsClient(externalMetricNode, 1)
                .withInteropNumberOfValidators(TOTAL_VALIDATOR_COUNT)
                .withInteropValidators(0, ACTIVE_VALIDATOR_COUNT)
                .build());
    tekuNode.start();

    externalMetricNode.waitForBeaconNodeMetricPublication();
    externalMetricNode.waitForValidatorMetricPublication(ACTIVE_VALIDATOR_COUNT);
    externalMetricNode.waitForSystemMetricPublication();

    tekuNode.stop();
    externalMetricNode.stop();
  }
}
