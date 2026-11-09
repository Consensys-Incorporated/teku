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

package tech.pegasys.teku.beaconrestapi.tekuv1.beacon;

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
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetAllBlobSidecarsAtSlot;
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

public class GetAllBlobSidecarsAtSlotIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void beforeEach() {
    startRestApiAtGenesisStoringNonCanonicalBlocks(SpecMilestone.DENEB);
  }

  @Test
  public void shouldGetNonCanonicalBlobSidecars() throws IOException {
    chainUpdater.blockOptions.setGenerateRandomBlobs(true);
    chainUpdater.blockOptions.setGenerateRandomBlobsCount(Optional.of(4));

    final UInt64 targetSlot = UInt64.valueOf(3);
    chainUpdater.advanceChainUntil(targetSlot);

    final ChainBuilder fork = chainBuilder.fork();
    final SignedBlockAndState nonCanonicalBlock = fork.generateNextBlock(chainUpdater.blockOptions);

    final List<BlobSidecar> nonCanonicalBlobSidecars =
        fork.getBlobSidecars(nonCanonicalBlock.getRoot());
    chainUpdater.saveBlock(nonCanonicalBlock, nonCanonicalBlobSidecars);

    final SignedBlockAndState canonicalBlock =
        chainBuilder.generateNextBlock(1, chainUpdater.blockOptions);
    final List<BlobSidecar> canonicalBlobSidecars =
        chainBuilder.getBlobSidecars(canonicalBlock.getRoot());
    chainUpdater.saveBlock(canonicalBlock, canonicalBlobSidecars);
    chainUpdater.updateBestBlock(canonicalBlock);
    chainUpdater.finalizeEpoch(targetSlot.plus(1));

    final Response nonCanonicalBlobSidecarResponse =
        get(
            nonCanonicalBlock.getSlot(),
            List.of(UInt64.ZERO, UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3)));

    assertThat(nonCanonicalBlobSidecarResponse.code()).isEqualTo(SC_OK);

    final List<BlobSidecar> nonCanonicalResult = parseBlobSidecars(nonCanonicalBlobSidecarResponse);
    assertThat(nonCanonicalResult).isEqualTo(nonCanonicalBlobSidecars);

    final Response canonicalBlobSidecarResponse = get(canonicalBlock.getSlot());

    assertThat(canonicalBlobSidecarResponse.code()).isEqualTo(SC_OK);

    final List<BlobSidecar> canonicalResult = parseBlobSidecars(canonicalBlobSidecarResponse);
    assertThat(canonicalResult).isEqualTo(canonicalBlobSidecars);
  }

  @Test
  public void shouldGetNonCanonicalBlobSidecarsAsSsz() throws Exception {
    chainUpdater.blockOptions.setGenerateRandomBlobs(true);
    chainUpdater.blockOptions.setGenerateRandomBlobsCount(Optional.of(4));

    final UInt64 targetSlot = UInt64.valueOf(3);
    chainUpdater.advanceChainUntil(targetSlot);

    final ChainBuilder fork = chainBuilder.fork();
    final SignedBlockAndState nonCanonicalBlock = fork.generateNextBlock(chainUpdater.blockOptions);

    final List<BlobSidecar> nonCanonicalBlobSidecars =
        fork.getBlobSidecars(nonCanonicalBlock.getRoot());
    chainUpdater.saveBlock(nonCanonicalBlock, nonCanonicalBlobSidecars);

    final SignedBlockAndState canonicalBlock =
        chainBuilder.generateNextBlock(1, chainUpdater.blockOptions);
    final List<BlobSidecar> canonicalBlobSidecars =
        chainBuilder.getBlobSidecars(canonicalBlock.getRoot());
    chainUpdater.saveBlock(canonicalBlock, canonicalBlobSidecars);
    chainUpdater.updateBestBlock(canonicalBlock);
    chainUpdater.finalizeEpoch(targetSlot.plus(1));

    final Response nonCanonicalBlobSidecarResponse =
        get(
            nonCanonicalBlock.getSlot(),
            List.of(UInt64.ZERO, UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3)),
            OCTET_STREAM);
    assertThat(nonCanonicalBlobSidecarResponse.code()).isEqualTo(SC_OK);

    final List<BlobSidecar> nonCanonicalResult =
        parseBlobSidecarsFromSsz(nonCanonicalBlobSidecarResponse);
    assertThat(nonCanonicalResult).isEqualTo(nonCanonicalBlobSidecars);

    final Response canonicalBlobSidecarResponse = get(canonicalBlock.getSlot());

    assertThat(canonicalBlobSidecarResponse.code()).isEqualTo(SC_OK);

    final List<BlobSidecar> canonicalResult = parseBlobSidecars(canonicalBlobSidecarResponse);
    assertThat(canonicalResult).isEqualTo(canonicalBlobSidecars);
  }

  @Test
  public void shouldGetNotFound() throws Exception {
    final Response response = get(UInt64.ONE);
    assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
  }

  public Response get(final UInt64 slot, final List<UInt64> indices, final String contentType)
      throws IOException {
    return getResponse(
        GetAllBlobSidecarsAtSlot.ROUTE.replace("{slot}", slot.toString()),
        Map.of(
            BLOB_INDICES_PARAMETER.getName(),
            indices.stream().map(UInt64::toString).collect(Collectors.joining(","))),
        contentType);
  }

  public Response get(final UInt64 slot, final List<UInt64> indices) throws IOException {
    return getResponse(
        GetAllBlobSidecarsAtSlot.ROUTE.replace("{slot}", slot.toString()),
        Map.of(
            BLOB_INDICES_PARAMETER.getName(),
            indices.stream().map(UInt64::toString).collect(Collectors.joining(","))));
  }

  public Response get(final UInt64 slot) throws IOException {
    return getResponse(GetAllBlobSidecarsAtSlot.ROUTE.replace("{slot}", slot.toString()));
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
