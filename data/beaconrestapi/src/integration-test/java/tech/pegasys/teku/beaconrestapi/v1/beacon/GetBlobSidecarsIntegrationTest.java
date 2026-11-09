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
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BLOB_INDICES_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlobSidecars;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class GetBlobSidecarsIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void beforeEach() {
    startRestApiAtGenesisStoringNonCanonicalBlocks(SpecMilestone.DENEB);
  }

  @Test
  public void shouldGetBlobSidecars() throws Exception {
    // generate 4 blobs per block
    chainUpdater.blockOptions.setGenerateRandomBlobs(true);
    chainUpdater.blockOptions.setGenerateRandomBlobsCount(Optional.of(4));

    // up to slot 3
    final UInt64 targetSlot = UInt64.valueOf(3);
    final SignedBlockAndState lastBlock = chainUpdater.advanceChainUntil(targetSlot);
    chainUpdater.updateBestBlock(lastBlock);
    final List<BlobSidecar> expected =
        recentChainData.getBlobSidecars(lastBlock.getSlotAndBlockRoot()).get();

    final Response responseAll = get("head");
    assertThat(responseAll.code()).isEqualTo(SC_OK);

    final List<BlobSidecar> actualAll = parseBlobSidecars(responseAll);
    assertThat(actualAll).hasSize(expected.size());
    assertThat(actualAll).isEqualTo(expected);

    final Response responseFiltered = get("head", List.of(UInt64.ZERO, UInt64.valueOf(2)));
    assertThat(responseFiltered.code()).isEqualTo(SC_OK);

    final List<BlobSidecar> actualFiltered = parseBlobSidecars(responseFiltered);
    assertThat(actualFiltered).hasSize(2);
    assertThat(actualFiltered).isEqualTo(List.of(expected.get(0), expected.get(2)));
  }

  @Test
  public void shouldGetEmptyBlobSidecars() throws Exception {
    // up to slot 3 without blobs
    final UInt64 targetSlot = UInt64.valueOf(3);
    final SignedBlockAndState lastBlock = chainUpdater.advanceChain(targetSlot);
    chainUpdater.updateBestBlock(lastBlock);

    final Response responseAll = get("head");
    assertThat(responseAll.code()).isEqualTo(SC_OK);
    final List<BlobSidecar> actualAll = parseBlobSidecars(responseAll);
    assertThat(actualAll).isEmpty();

    final Response responseFiltered = get("head", List.of(UInt64.ZERO, UInt64.valueOf(2)));
    assertThat(responseFiltered.code()).isEqualTo(SC_OK);
    final List<BlobSidecar> actualFiltered = parseBlobSidecars(responseFiltered);
    assertThat(actualFiltered).isEmpty();
  }

  @Test
  public void shouldGetNotFound() throws Exception {
    // no chain yet, so definitely not existing block
    final Response responseAll = get("3");
    assertThat(responseAll.code()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  public void shouldGetBlobSidecarsAsSsz() throws Exception {
    // generate 4 blobs per block
    chainUpdater.blockOptions.setGenerateRandomBlobs(true);
    chainUpdater.blockOptions.setGenerateRandomBlobsCount(Optional.of(4));

    // up to slot 3
    final UInt64 targetSlot = UInt64.valueOf(3);
    final SignedBlockAndState lastBlock = chainUpdater.advanceChainUntil(targetSlot);
    chainUpdater.updateBestBlock(lastBlock);
    final List<BlobSidecar> expected =
        recentChainData.getBlobSidecars(lastBlock.getSlotAndBlockRoot()).get();

    final Response response = get("head", OCTET_STREAM);
    assertThat(response.code()).isEqualTo(SC_OK);

    final List<BlobSidecar> result = parseBlobSidecarsFromSsz(response);
    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void shouldGetEmptyBlobSidecarsAsSsz() throws Exception {
    // up to slot 3 without blobs
    final UInt64 targetSlot = UInt64.valueOf(3);
    final SignedBlockAndState lastBlock = chainUpdater.advanceChainUntil(targetSlot);
    chainUpdater.updateBestBlock(lastBlock);

    final Response response = get("head", OCTET_STREAM);
    assertThat(response.code()).isEqualTo(SC_OK);

    final List<BlobSidecar> result = parseBlobSidecarsFromSsz(response);
    assertThat(result).isEmpty();
  }

  @Test
  public void shouldGetNonCanonicalBlobSidecarsByRoot() throws IOException {
    chainUpdater.blockOptions.setGenerateRandomBlobs(true);
    chainUpdater.blockOptions.setGenerateRandomBlobsCount(Optional.of(4));

    createBlocksAtSlots(10);

    final ChainBuilder fork = chainBuilder.fork();
    final SignedBlockAndState nonCanonicalBlock = fork.generateNextBlock(chainUpdater.blockOptions);

    final List<BlobSidecar> nonCanonicalBlobSidecars =
        fork.getBlobSidecars(nonCanonicalBlock.getRoot());
    chainUpdater.saveBlock(nonCanonicalBlock, nonCanonicalBlobSidecars);

    final SignedBlockAndState canonicalBlock =
        chainBuilder.generateNextBlock(1, chainUpdater.blockOptions);
    chainUpdater.saveBlock(canonicalBlock, chainBuilder.getBlobSidecars(canonicalBlock.getRoot()));
    chainUpdater.updateBestBlock(canonicalBlock);

    final Response byRootResponse =
        get(
            nonCanonicalBlock.getRoot().toHexString(),
            List.of(UInt64.ZERO, UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3)));

    assertThat(byRootResponse.code()).isEqualTo(SC_OK);

    final List<BlobSidecar> byRootBlobSidecars = parseBlobSidecars(byRootResponse);
    assertThat(byRootBlobSidecars).isEqualTo(nonCanonicalBlobSidecars);

    final Response bySlotResponse =
        get(
            nonCanonicalBlock.getSlot().toString(),
            List.of(UInt64.ZERO, UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3)));

    assertThat(bySlotResponse.code()).isEqualTo(SC_NOT_FOUND);
  }

  public Response get(final String blockIdString, final String contentType) throws IOException {
    return getResponse(GetBlobSidecars.ROUTE.replace("{block_id}", blockIdString), contentType);
  }

  public Response get(final String blockIdString) throws IOException {
    return getResponse(GetBlobSidecars.ROUTE.replace("{block_id}", blockIdString));
  }

  public Response get(final String blockIdString, final List<UInt64> indices) throws IOException {
    return getResponse(
        GetBlobSidecars.ROUTE.replace("{block_id}", blockIdString),
        Map.of(
            BLOB_INDICES_PARAMETER.getName(),
            indices.stream().map(UInt64::toString).collect(Collectors.joining(","))));
  }

  private List<BlobSidecar> parseBlobSidecars(final Response response) throws IOException {
    final DeserializableTypeDefinition<BlobSidecar> blobSidecarTypeDefinition =
        SchemaDefinitionsDeneb.required(spec.getGenesisSchemaDefinitions())
            .getBlobSidecarSchema()
            .getJsonTypeDefinition();
    final DeserializableTypeDefinition<List<BlobSidecar>> jsonTypeDefinition =
        SharedApiTypes.withDataWrapper("blobSidecars", listOf(blobSidecarTypeDefinition));
    return JsonUtil.parse(response.body().string(), jsonTypeDefinition);
  }

  private List<BlobSidecar> parseBlobSidecarsFromSsz(final Response response) throws IOException {
    final BlobSidecarSchema blobSidecarSchema =
        SchemaDefinitionsDeneb.required(spec.getGenesisSchemaDefinitions()).getBlobSidecarSchema();
    SszListSchema<BlobSidecar, ? extends SszList<BlobSidecar>> blobSidecarSszListSchema =
        SszListSchema.create(
            blobSidecarSchema, SpecConfigDeneb.required(specConfig).getMaxBlobsPerBlock());
    return blobSidecarSszListSchema.sszDeserialize(Bytes.of(response.body().bytes())).asList();
  }
}
