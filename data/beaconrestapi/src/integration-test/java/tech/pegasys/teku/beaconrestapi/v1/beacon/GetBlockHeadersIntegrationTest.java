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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeaders;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;

public class GetBlockHeadersIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  final List<SignedBlockAndState> canonicalBlockAndStateList = new ArrayList<>();
  final List<SignedBlockAndState> nonCanonicalBlockAndStateList = new ArrayList<>();

  @BeforeEach
  public void setup() {
    startRestApiAtGenesisStoringNonCanonicalBlocks(SpecMilestone.PHASE0);
  }

  @Test
  public void shouldGetBlockHeaders() throws IOException {
    createBlocksAtSlots(10);
    SignedBlockAndState head = chainBuilder.generateNextBlock();
    chainUpdater.updateBestBlock(head);

    final Response response = get();

    final JsonNode responseBody = OBJECT_MAPPER.readTree(response.body().string());
    final JsonNode data = responseBody.get("data");
    assertThat(data.size()).isGreaterThan(0);
    assertThat(data.get(0).get("root").asText()).isEqualTo(head.getRoot().toHexString());
  }

  @Test
  public void shouldGetBlockHeadersBySlot() throws IOException {
    createBlocksAtSlots(10);
    SignedBlockAndState parent = chainBuilder.generateNextBlock();
    chainUpdater.updateBestBlock(parent);
    SignedBlockAndState head = chainBuilder.generateNextBlock();
    chainUpdater.updateBestBlock(head);

    final JsonNode data = get(parent.getSlot());

    assertThat(data.size()).isGreaterThan(0);
    assertThat(data.get(0).get("root").asText()).isEqualTo(parent.getRoot().toHexString());
  }

  @ParameterizedTest(name = "finalized={0}")
  @ValueSource(booleans = {true, false})
  public void shouldGetNonCanonicalHeadersBySlot(final boolean isFinalized) throws IOException {
    setupData();

    // PRE: we have a non-canonical block and a canonical block at the same slot
    final SignedBeaconBlock canonical = canonicalBlockAndStateList.getLast().getBlock();
    final SignedBeaconBlock forked = nonCanonicalBlockAndStateList.get(1).getBlock();
    assertThat(forked.getSlot()).isEqualTo(canonical.getSlot());
    if (isFinalized) {
      chainUpdater.finalizeEpoch(2);
    }
    final JsonNode data = get(nonCanonicalBlockAndStateList.get(1).getSlot());

    assertThat(data.size()).isGreaterThan(1);
    assertThat(data.get(0).get("root").asText()).isEqualTo(forked.getRoot().toHexString());
    assertThat(data.get(0).get("canonical").asBoolean()).isFalse();
    assertThat(data.get(1).get("root").asText()).isEqualTo(canonical.getRoot().toHexString());
    assertThat(data.get(1).get("canonical").asBoolean()).isTrue();
  }

  private void setupData() {
    canonicalBlockAndStateList.addAll(createBlocksAtSlots(10));
    final ChainBuilder fork = chainBuilder.fork();
    nonCanonicalBlockAndStateList.add(fork.generateNextBlock());
    chainUpdater.saveBlock(nonCanonicalBlockAndStateList.getLast());
    nonCanonicalBlockAndStateList.add(fork.generateNextBlock());
    chainUpdater.saveBlock(nonCanonicalBlockAndStateList.getLast());
    canonicalBlockAndStateList.add(chainBuilder.generateNextBlock(1));
    chainUpdater.updateBestBlock(canonicalBlockAndStateList.getLast());
    chainUpdater.advanceChain(32);
  }

  public Response get() throws IOException {
    return getResponse(GetBlockHeaders.ROUTE);
  }

  private JsonNode get(final UInt64 slot) throws IOException {
    final Response response = getResponse(GetBlockHeaders.ROUTE, Map.of("slot", slot.toString()));
    assertThat(response.code()).isEqualTo(SC_OK);
    return getResponseData(response);
  }
}
