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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Optional;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;

@TestSpecContext(milestone = {DENEB, ELECTRA, FULU})
public class GetNewBlockV3BlockContentsIntegrationTest
    extends AbstractGetNewBlockV3IntegrationTest {

  @TestTemplate
  void shouldGetUnBlindedBlockContentPostDenebAsJson() throws Exception {
    final BlockContainer blockContents = dataStructureUtil.randomBlockContents(ONE);
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(blockContents, ONE);
    final BLSSignature signature =
        blockContainerAndMetaData.blockContainer().getBlock().getBody().getRandaoReveal();
    when(validatorApiChannel.createUnsignedBlock(eq(UInt64.ONE), eq(signature), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockContainerAndMetaData)));
    Response response = get(signature, ContentTypes.JSON);
    assertResponseWithHeaders(
        response,
        false,
        blockContainerAndMetaData.executionPayloadValue(),
        blockContainerAndMetaData.consensusBlockValue());

    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    final JsonNode expectedAsJsonNode =
        JsonTestUtil.parseAsJsonNode(getExpectedBlockAsJson(specMilestone, false, true));

    assertThat(resultAsJsonNode).isEqualTo(expectedAsJsonNode);
  }

  @TestTemplate
  void shouldGetUnBlindedBlockContentPostDenebAsSsz() throws IOException {
    final BlockContainer blockContents = dataStructureUtil.randomBlockContents(ONE);
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(blockContents, ONE);
    final BLSSignature signature = blockContents.getBlock().getBody().getRandaoReveal();
    when(validatorApiChannel.createUnsignedBlock(eq(UInt64.ONE), eq(signature), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockContainerAndMetaData)));
    Response response = get(signature, ContentTypes.OCTET_STREAM);
    assertResponseWithHeaders(
        response,
        false,
        blockContainerAndMetaData.executionPayloadValue(),
        blockContainerAndMetaData.consensusBlockValue());
    final BlockContainer result =
        spec.getGenesisSchemaDefinitions()
            .getBlockContainerSchema()
            .sszDeserialize(Bytes.of(response.body().bytes()));
    assertThat(result).isEqualTo(blockContents);
  }
}
