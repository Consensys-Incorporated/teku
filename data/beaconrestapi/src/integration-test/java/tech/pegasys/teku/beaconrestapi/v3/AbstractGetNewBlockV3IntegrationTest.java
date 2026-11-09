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

package tech.pegasys.teku.beaconrestapi.v3;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_VALUE;

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v3.validator.GetNewBlockV3;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.util.DataStructureUtil;

abstract class AbstractGetNewBlockV3IntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  private static final Logger LOG = LogManager.getLogger();
  protected DataStructureUtil dataStructureUtil;
  protected SpecMilestone specMilestone;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    startRestAPIAtGenesis(specMilestone);
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  protected Response get(final BLSSignature signature, final String contentType)
      throws IOException {
    return getResponse(
        GetNewBlockV3.ROUTE.replace("{slot}", "1"),
        Map.of("randao_reveal", signature.toString()),
        contentType);
  }

  protected String getExpectedBlockAsJson(
      final SpecMilestone specMilestone, final boolean blinded, final boolean blockContents)
      throws IOException {
    final String fileName =
        String.format(
            "new%s%s%s.json",
            blinded ? "Blinded" : "",
            blockContents ? "BlockContents" : "Block",
            specMilestone.name());

    LOG.info("Read expected json file: {}", fileName);
    return Resources.toString(
        Resources.getResource(AbstractGetNewBlockV3IntegrationTest.class, fileName), UTF_8);
  }

  protected void assertResponseWithHeaders(
      final Response response,
      final boolean blinded,
      final UInt256 executionPayloadValue,
      final UInt256 consensusBlockValue) {
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(specMilestone.name().toLowerCase(Locale.ROOT));
    assertThat(response.header(HEADER_EXECUTION_PAYLOAD_BLINDED))
        .isEqualTo(Boolean.toString(blinded));
    assertThat(response.header(HEADER_EXECUTION_PAYLOAD_VALUE))
        .isEqualTo(executionPayloadValue.toDecimalString());
    assertThat(response.header(HEADER_CONSENSUS_BLOCK_VALUE))
        .isEqualTo(consensusBlockValue.toDecimalString());
  }
}
