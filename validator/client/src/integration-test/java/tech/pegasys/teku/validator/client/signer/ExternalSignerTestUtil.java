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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.matchers.MatchType.STRICT;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.JsonBody;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

class ExternalSignerTestUtil {

  static void verifySignRequest(
      final ClientAndServer client,
      final String publicKey,
      final SigningRequestBody signingRequestBody,
      final SchemaDefinitions schemaDefinitions)
      throws JsonProcessingException {
    final JsonBody body =
        json(
            JsonUtil.serialize(
                signingRequestBody, signingRequestBody.getJsonTypeDefinition(schemaDefinitions)),
            STRICT);
    client.verify(
        request()
            .withMethod("POST")
            .withBody(body)
            .withHeader("Content-Type", "application/json")
            .withPath(ExternalSigner.EXTERNAL_SIGNER_ENDPOINT + "/" + publicKey));
  }

  static void validateMetrics(
      final StubMetricsSystem metricsSystem,
      final long successCount,
      final long failCount,
      final long timeoutCount) {

    assertThat(
            metricsSystem.getLabelledCounterValue(
                TekuMetricCategory.VALIDATOR, "external_signer_requests_total", "success"))
        .isEqualTo(successCount);
    assertThat(
            metricsSystem.getLabelledCounterValue(
                TekuMetricCategory.VALIDATOR, "external_signer_requests_total", "failed"))
        .isEqualTo(failCount);
    assertThat(
            metricsSystem.getLabelledCounterValue(
                TekuMetricCategory.VALIDATOR, "external_signer_requests_total", "timeout"))
        .isEqualTo(timeoutCount);
  }
}
