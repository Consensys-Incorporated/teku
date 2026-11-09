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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlindedBlock;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;

public class GetBlindedBlockIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  @Test
  public void shouldGetBlindedPhase0Block() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    final List<SignedBlockAndState> created = createBlocksAtSlots(10);
    final Response response = get("head");

    final SignedBeaconBlock result = parseBlock(response);

    // Phase0 blocks are always unblinded
    assertThat(result.isBlinded()).isFalse();
    assertThat(result).isEqualTo(created.get(0).getBlock());
    assertThat(result.hashTreeRoot()).isEqualTo(created.get(0).getBlock().hashTreeRoot());
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.PHASE0.lowerCaseName());
  }

  @Test
  public void shouldGetBlindedBellatrixBlock() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.BELLATRIX);
    final List<SignedBlockAndState> created = createBlocksAtSlots(10);
    final Response response = get("head");

    final SignedBeaconBlock result = parseBlock(response);

    assertThat(result.isBlinded()).isTrue();
    assertThat(result.hashTreeRoot()).isEqualTo(created.get(0).getBlock().hashTreeRoot());
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.BELLATRIX.lowerCaseName());
  }

  @Test
  public void shouldGetBellatrixBlockAsSsz() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.BELLATRIX);
    final List<SignedBlockAndState> created = createBlocksAtSlots(10);
    final Response response = get("head", OCTET_STREAM);
    assertThat(response.code()).isEqualTo(SC_OK);

    final SignedBeaconBlock result =
        spec.getGenesisSchemaDefinitions()
            .getSignedBlindedBeaconBlockSchema()
            .sszDeserialize(Bytes.of(response.body().bytes()));
    assertThat(result.isBlinded()).isTrue();
    assertThat(result.hashTreeRoot()).isEqualTo(created.get(0).getBlock().hashTreeRoot());
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.BELLATRIX.lowerCaseName());
  }

  public Response get(final String blockIdString, final String contentType) throws IOException {
    return getResponse(GetBlindedBlock.ROUTE.replace("{block_id}", blockIdString), contentType);
  }

  public Response get(final String blockIdString) throws IOException {
    return getResponse(GetBlindedBlock.ROUTE.replace("{block_id}", blockIdString));
  }

  private SignedBeaconBlock parseBlock(final Response response) throws IOException {
    final DeserializableTypeDefinition<SignedBeaconBlock> jsonTypeDefinition =
        SharedApiTypes.withDataWrapper(
            spec.getGenesisSchemaDefinitions().getSignedBlindedBeaconBlockSchema());
    final SignedBeaconBlock result = JsonUtil.parse(response.body().string(), jsonTypeDefinition);
    return result;
  }
}
