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

package tech.pegasys.teku.storage.client;

import static java.util.Collections.emptyList;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

/**
 * PeerDAS BlobSidecar provider, when we don't store blobSidecars in DB as is but could reconstruct
 * from DataColumnSidecar's
 */
public class BlobSidecarReconstructionProvider {
  private final CombinedChainDataClient combinedChainDataClient;
  private final Spec spec;

  public BlobSidecarReconstructionProvider(
      final CombinedChainDataClient combinedChainDataClient, final Spec spec) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.spec = spec;
  }

  public SafeFuture<List<BlobSidecar>> reconstructBlobSidecars(
      final UInt64 slot, final List<UInt64> blobIndices) {
    return combinedChainDataClient
        .getBlockAtSlotExact(slot)
        .thenCompose(
            maybeBlock -> {
              if (maybeBlock.isEmpty()) {
                return SafeFuture.completedFuture(emptyList());
              }
              return reconstructBlobSidecars(maybeBlock.get().getSlotAndBlockRoot(), blobIndices);
            });
  }

  public SafeFuture<List<BlobSidecar>> reconstructBlobSidecars(
      final SlotAndBlockRoot slotAndBlockRoot, final List<UInt64> blobIndices) {
    final SafeFuture<List<DataColumnSlotAndIdentifier>> dataColumnIdentifiersFuture =
        combinedChainDataClient.getDataColumnIdentifiers(slotAndBlockRoot.getSlot());
    return dataColumnIdentifiersFuture.thenCompose(
        dataColumnIdentifiers -> {
          if (dataColumnIdentifiers.isEmpty()) {
            return SafeFuture.completedFuture(emptyList());
          }
          final Set<DataColumnSlotAndIdentifier> dbIdentifiers =
              new HashSet<>(dataColumnIdentifiers);
          final List<DataColumnSlotAndIdentifier> requiredIdentifiers =
              Stream.iterate(
                      UInt64.ZERO,
                      // We need the first 50% for reconstruction
                      index -> index.isLessThan(spec.getNumberOfDataColumns().orElseThrow() / 2),
                      UInt64::increment)
                  .map(
                      index ->
                          new DataColumnSlotAndIdentifier(
                              slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), index))
                  .toList();
          if (requiredIdentifiers.stream()
              .anyMatch(identifier -> !dbIdentifiers.contains(identifier))) {
            // We do not have the data columns required for reconstruction
            return SafeFuture.completedFuture(emptyList());
          }
          return reconstructBlobSidecarsForIdentifiers(requiredIdentifiers, blobIndices);
        });
  }

  private SafeFuture<List<BlobSidecar>> reconstructBlobSidecarsForIdentifiers(
      final List<DataColumnSlotAndIdentifier> slotAndIdentifiers, final List<UInt64> blobIndices) {
    return SafeFuture.collectAll(
            slotAndIdentifiers.stream().map(combinedChainDataClient::getSidecar))
        .thenCompose(
            sidecarOptionals -> {
              if (sidecarOptionals.stream().anyMatch(Optional::isEmpty)) {
                // We will not be able to reconstruct if there is a gap
                return SafeFuture.completedFuture(emptyList());
              }
              final List<DataColumnSidecar> dataColumnSidecars =
                  sidecarOptionals.stream().map(Optional::orElseThrow).toList();

              final SafeFuture<Optional<SignedBeaconBlock>> signedBeaconBlockFuture =
                  combinedChainDataClient.getBlockByBlockRoot(
                      slotAndIdentifiers.getFirst().blockRoot());
              return signedBeaconBlockFuture.thenApply(
                  signedBeaconBlock ->
                      signedBeaconBlock
                          .map(
                              block ->
                                  reconstructBlobSidecarsFromDataColumns(
                                      dataColumnSidecars, block, blobIndices))
                          .orElse(emptyList()));
            });
  }

  private List<BlobSidecar> reconstructBlobSidecarsFromDataColumns(
      final List<DataColumnSidecar> dataColumnSidecars,
      final SignedBeaconBlock signedBeaconBlock,
      final List<UInt64> blobIndices) {
    if (dataColumnSidecars.isEmpty()) {
      return emptyList();
    }

    final MiscHelpersDeneb miscHelpersDeneb =
        MiscHelpersDeneb.required(spec.forMilestone(SpecMilestone.DENEB).miscHelpers());
    final SchemaDefinitionsDeneb schemaDefinitionsDeneb =
        SchemaDefinitionsDeneb.required(
            spec.forMilestone(SpecMilestone.DENEB).getSchemaDefinitions());

    return IntStream.range(0, dataColumnSidecars.getFirst().getKzgProofs().size())
        .filter(index -> blobIndices.isEmpty() || blobIndices.contains(UInt64.valueOf(index)))
        .mapToObj(
            blobIndex ->
                constructBlobSidecar(
                    dataColumnSidecars,
                    blobIndex,
                    signedBeaconBlock,
                    miscHelpersDeneb,
                    schemaDefinitionsDeneb))
        .toList();
  }

  private BlobSidecar constructBlobSidecar(
      final List<DataColumnSidecar> dataColumnSidecars,
      final int blobIndex,
      final SignedBeaconBlock signedBeaconBlock,
      final MiscHelpersDeneb miscHelpersDeneb,
      final SchemaDefinitionsDeneb schemaDefinitionsDeneb) {
    final Bytes blobBytes =
        dataColumnSidecars.stream()
            .map(dataColumnSidecar -> dataColumnSidecar.getColumn().get(blobIndex).getBytes())
            .reduce(Bytes::concatenate)
            .orElseThrow();
    return miscHelpersDeneb.constructBlobSidecar(
        signedBeaconBlock,
        UInt64.valueOf(blobIndex),
        schemaDefinitionsDeneb.getBlobSchema().create(blobBytes),
        // The blob proof is not necessary; only cell proofs matter now
        new SszKZGProof(KZGProof.ZERO));
  }
}
