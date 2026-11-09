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
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.VERSIONED_HASHES_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlobs;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class GetBlobsIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  final Function<SszKZGCommitment, Bytes32> commitmentToVersionedHash =
      sszKzgCommitment ->
          MiscHelpersDeneb.required(spec.forMilestone(SpecMilestone.DENEB).miscHelpers())
              .kzgCommitmentToVersionedHash(sszKzgCommitment.getKZGCommitment())
              .get();

  @BeforeEach
  public void beforeEach() {
    startRestApiAtGenesisStoringNonCanonicalBlocks(SpecMilestone.DENEB);
  }

  @Test
  public void shouldGetBlobs() throws Exception {
    // generate 4 blobs per block
    chainUpdater.blockOptions.setGenerateRandomBlobs(true);
    chainUpdater.blockOptions.setGenerateRandomBlobsCount(Optional.of(4));

    // up to slot 3
    final UInt64 targetSlot = UInt64.valueOf(3);
    final SignedBlockAndState lastBlock = chainUpdater.advanceChainUntil(targetSlot);
    chainUpdater.updateBestBlock(lastBlock);
    final List<Blob> expected =
        recentChainData.getBlobSidecars(lastBlock.getSlotAndBlockRoot()).get().stream()
            .map(BlobSidecar::getBlob)
            .toList();

    final Response responseAll = get("head");
    assertThat(responseAll.code()).isEqualTo(SC_OK);

    final List<Blob> actualAll = parseBlobs(responseAll);
    assertThat(actualAll).hasSize(expected.size());
    assertThat(actualAll).isEqualTo(expected);

    final SszList<SszKZGCommitment> sszKZGCommitments =
        lastBlock.getBlock().getMessage().getBody().getOptionalBlobKzgCommitments().get();
    final Response responseFiltered =
        get(
            "head",
            List.of(
                commitmentToVersionedHash.apply(sszKZGCommitments.get(0)),
                commitmentToVersionedHash.apply(sszKZGCommitments.get(2))));
    assertThat(responseFiltered.code()).isEqualTo(SC_OK);

    final List<Blob> actualFiltered = parseBlobs(responseFiltered);
    assertThat(actualFiltered).hasSize(2);
    assertThat(actualFiltered).isEqualTo(List.of(expected.get(0), expected.get(2)));
  }

  @Test
  public void shouldGetEmptyBlobs() throws Exception {
    // up to slot 3 without blobs
    final UInt64 targetSlot = UInt64.valueOf(3);
    final SignedBlockAndState lastBlock = chainUpdater.advanceChain(targetSlot);
    chainUpdater.updateBestBlock(lastBlock);

    final Response responseAll = get("head");
    assertThat(responseAll.code()).isEqualTo(SC_OK);
    final List<Blob> actualAll = parseBlobs(responseAll);
    assertThat(actualAll).isEmpty();

    final Response responseFiltered = get("head", List.of(Bytes32.random(), Bytes32.random()));
    assertThat(responseFiltered.code()).isEqualTo(SC_OK);
    final List<Blob> actualFiltered = parseBlobs(responseFiltered);
    assertThat(actualFiltered).isEmpty();
  }

  @Test
  public void shouldGetNotFound() throws Exception {
    // no chain yet, so definitely not existing block
    final Response responseAll = get("3");
    assertThat(responseAll.code()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  public void shouldGetBlobsAsSsz() throws Exception {
    // generate 4 blobs per block
    chainUpdater.blockOptions.setGenerateRandomBlobs(true);
    chainUpdater.blockOptions.setGenerateRandomBlobsCount(Optional.of(4));

    // up to slot 3
    final UInt64 targetSlot = UInt64.valueOf(3);
    final SignedBlockAndState lastBlock = chainUpdater.advanceChainUntil(targetSlot);
    chainUpdater.updateBestBlock(lastBlock);
    final List<Blob> expected =
        recentChainData.getBlobSidecars(lastBlock.getSlotAndBlockRoot()).get().stream()
            .map(BlobSidecar::getBlob)
            .toList();

    final Response response = get("head", OCTET_STREAM);
    assertThat(response.code()).isEqualTo(SC_OK);

    final List<Blob> result = parseBlobsFromSsz(response);
    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void shouldGetEmptyBlobsAsSsz() throws Exception {
    // up to slot 3 without blobs
    final UInt64 targetSlot = UInt64.valueOf(3);
    final SignedBlockAndState lastBlock = chainUpdater.advanceChainUntil(targetSlot);
    chainUpdater.updateBestBlock(lastBlock);

    final Response response = get("head", OCTET_STREAM);
    assertThat(response.code()).isEqualTo(SC_OK);

    final List<Blob> result = parseBlobsFromSsz(response);
    assertThat(result).isEmpty();
  }

  @Test
  public void shouldGetNonCanonicalBlobsByRoot() throws IOException {
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

    final SszList<SszKZGCommitment> sszKZGCommitments =
        nonCanonicalBlock.getBlock().getMessage().getBody().getOptionalBlobKzgCommitments().get();
    final Response byRootResponse =
        get(
            nonCanonicalBlock.getRoot().toHexString(),
            List.of(
                commitmentToVersionedHash.apply(sszKZGCommitments.get(0)),
                commitmentToVersionedHash.apply(sszKZGCommitments.get(1)),
                commitmentToVersionedHash.apply(sszKZGCommitments.get(2)),
                commitmentToVersionedHash.apply(sszKZGCommitments.get(3))));

    assertThat(byRootResponse.code()).isEqualTo(SC_OK);

    final List<Blob> byRootBlob = parseBlobs(byRootResponse);
    assertThat(byRootBlob)
        .isEqualTo(nonCanonicalBlobSidecars.stream().map(BlobSidecar::getBlob).toList());

    final Response bySlotResponse =
        get(
            nonCanonicalBlock.getSlot().toString(),
            List.of(
                commitmentToVersionedHash.apply(sszKZGCommitments.get(0)),
                commitmentToVersionedHash.apply(sszKZGCommitments.get(1)),
                commitmentToVersionedHash.apply(sszKZGCommitments.get(2)),
                commitmentToVersionedHash.apply(sszKZGCommitments.get(3))));

    assertThat(bySlotResponse.code()).isEqualTo(SC_NOT_FOUND);
  }

  public Response get(final String blockIdString, final String contentType) throws IOException {
    return getResponse(GetBlobs.ROUTE.replace("{block_id}", blockIdString), contentType);
  }

  public Response get(final String blockIdString) throws IOException {
    return getResponse(GetBlobs.ROUTE.replace("{block_id}", blockIdString));
  }

  public Response get(final String blockIdString, final List<Bytes32> versionedHashes)
      throws IOException {
    return getResponse(
        GetBlobs.ROUTE.replace("{block_id}", blockIdString),
        Map.of(
            VERSIONED_HASHES_PARAMETER.getName(),
            versionedHashes.stream().map(Bytes32::toString).collect(Collectors.joining(","))));
  }

  private List<Blob> parseBlobs(final Response response) throws IOException {
    final DeserializableTypeDefinition<Blob> blobTypeDefinition =
        SchemaDefinitionsDeneb.required(spec.getGenesisSchemaDefinitions())
            .getBlobSchema()
            .getJsonTypeDefinition();
    final DeserializableTypeDefinition<List<Blob>> jsonTypeDefinition =
        SharedApiTypes.withDataWrapper("blobs", listOf(blobTypeDefinition));
    return JsonUtil.parse(response.body().string(), jsonTypeDefinition);
  }

  private List<Blob> parseBlobsFromSsz(final Response response) throws IOException {
    final BlobSchema blobSchema =
        SchemaDefinitionsDeneb.required(spec.getGenesisSchemaDefinitions()).getBlobSchema();
    SszListSchema<Blob, ? extends SszList<Blob>> blobSszListSchema =
        SszListSchema.create(
            blobSchema, SpecConfigDeneb.required(specConfig).getMaxBlobsPerBlock());
    return blobSszListSchema.sszDeserialize(Bytes.of(response.body().bytes())).asList();
  }
}
