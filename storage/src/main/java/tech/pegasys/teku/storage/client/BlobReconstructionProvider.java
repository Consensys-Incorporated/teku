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

import com.google.common.collect.Streams;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.storage.api.DataColumnSidecarNetworkRetriever;

/** Blob provider for post-FULU, reconstructs Blobs from DataColumnSidecars */
public class BlobReconstructionProvider {
  private static final Logger LOG = LogManager.getLogger();

  private final CombinedChainDataClient combinedChainDataClient;
  private final Spec spec;
  private final ExtensionBlobReconstructor extensionBlobReconstructor;
  private final CryptoBlobReconstructor cryptoBlobReconstructor;
  private final NetworkBlobReconstructor networkBlobReconstructor;

  protected BlobReconstructionProvider(
      final CombinedChainDataClient combinedChainDataClient,
      final Spec spec,
      final ExtensionBlobReconstructor extensionBlobReconstructor,
      final CryptoBlobReconstructor cryptoBlobReconstructor,
      final NetworkBlobReconstructor networkBlobReconstructor) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.spec = spec;
    this.extensionBlobReconstructor = extensionBlobReconstructor;
    this.cryptoBlobReconstructor = cryptoBlobReconstructor;
    this.networkBlobReconstructor = networkBlobReconstructor;
  }

  public static BlobReconstructionProvider create(
      final CombinedChainDataClient combinedChainDataClient,
      final DataColumnSidecarNetworkRetriever networkRetriever,
      final Spec spec) {

    final Supplier<BlobSchema> blobSchemaSupplier =
        () ->
            SchemaDefinitionsDeneb.required(
                    spec.forMilestone(SpecMilestone.DENEB).getSchemaDefinitions())
                .getBlobSchema();

    final ExtensionBlobReconstructor extensionBlobReconstructor =
        new ExtensionBlobReconstructor(spec, blobSchemaSupplier);
    final CryptoBlobReconstructor cryptoBlobReconstructor =
        new CryptoBlobReconstructor(spec, blobSchemaSupplier);
    final NetworkBlobReconstructor networkBlobReconstructor =
        new NetworkBlobReconstructor(spec, blobSchemaSupplier, networkRetriever);

    return new BlobReconstructionProvider(
        combinedChainDataClient,
        spec,
        extensionBlobReconstructor,
        cryptoBlobReconstructor,
        networkBlobReconstructor);
  }

  public SafeFuture<List<Blob>> reconstructBlobs(
      final SlotAndBlockRoot slotAndBlockRoot,
      final List<UInt64> blobIndices,
      final boolean isCanonical) {

    return reconstructBlobSidecarsStartingWithExtensionReconstructor(
        slotAndBlockRoot, blobIndices, isCanonical);
  }

  private List<DataColumnSlotAndIdentifier> createIdentifiers(
      final SlotAndBlockRoot slotAndBlockRoot,
      final UInt64 fromIndexInclusive,
      final UInt64 toIndexExclusive) {
    return Stream.iterate(
            fromIndexInclusive, index -> index.isLessThan(toIndexExclusive), UInt64::increment)
        .map(
            index ->
                new DataColumnSlotAndIdentifier(
                    slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), index))
        .toList();
  }

  private SafeFuture<List<Optional<DataColumnSidecar>>> fetchDataColumnSidecars(
      final List<DataColumnSlotAndIdentifier> slotAndIdentifiers, final boolean isCanonical) {
    return SafeFuture.collectAll(
        slotAndIdentifiers.stream()
            .map(
                identifier ->
                    isCanonical
                        ? combinedChainDataClient.getSidecar(identifier)
                        : combinedChainDataClient.getNonCanonicalSidecar(identifier)));
  }

  private SafeFuture<List<Blob>> reconstructBlobSidecarsStartingWithExtensionReconstructor(
      final SlotAndBlockRoot slotAndBlockRoot,
      final List<UInt64> blobIndices,
      final boolean isCanonical) {

    LOG.trace("Trying to reconstruct blobs with extension reconstructor for {}", slotAndBlockRoot);

    // We need the first 50% for reconstruction
    final List<DataColumnSlotAndIdentifier> firstHalfIdentifiers =
        createIdentifiers(
            slotAndBlockRoot,
            UInt64.ZERO,
            UInt64.valueOf(spec.getNumberOfDataColumns().orElseThrow() / 2));

    return fetchDataColumnSidecars(firstHalfIdentifiers, isCanonical)
        .thenCompose(
            sidecarOptionals -> {
              final List<DataColumnSidecar> firstHalfOfSidecars =
                  sidecarOptionals.stream().filter(Optional::isPresent).map(Optional::get).toList();

              // we try extension reconstruction first, which is the fastest one
              final SafeFuture<Optional<List<Blob>>> maybeReconstructedBlobs =
                  extensionBlobReconstructor.reconstructBlobs(
                      slotAndBlockRoot, firstHalfOfSidecars, blobIndices);

              return maybeReconstructedBlobs.thenCompose(
                  maybeBlobs -> {
                    if (maybeBlobs.isEmpty()) {
                      return reconstructBlobSidecarsStartingWithCryptoReconstructor(
                          firstHalfOfSidecars, slotAndBlockRoot, blobIndices, isCanonical);
                    } else {
                      LOG.debug(
                          "Blobs {} reconstructed for {} with extension reconstruction",
                          blobIndices,
                          slotAndBlockRoot);
                      return SafeFuture.completedFuture(maybeBlobs.get());
                    }
                  });
            });
  }

  private SafeFuture<List<Blob>> reconstructBlobSidecarsStartingWithCryptoReconstructor(
      final List<DataColumnSidecar> firstHalfOfSidecarsWithGaps,
      final SlotAndBlockRoot slotAndBlockRoot,
      final List<UInt64> blobIndices,
      final boolean isCanonical) {
    LOG.trace("Trying to reconstruct blobs with crypto reconstructor for {}", slotAndBlockRoot);
    final List<DataColumnSlotAndIdentifier> secondHalfIdentifiers =
        createIdentifiers(
            slotAndBlockRoot,
            UInt64.valueOf(spec.getNumberOfDataColumns().orElseThrow() / 2),
            UInt64.valueOf(spec.getNumberOfDataColumns().orElseThrow()));
    final SafeFuture<List<Optional<DataColumnSidecar>>> secondHalfSidecarFutures =
        fetchDataColumnSidecars(secondHalfIdentifiers, isCanonical);

    return secondHalfSidecarFutures.thenCompose(
        secondHalfSidecars -> {
          final List<DataColumnSidecar> existingSidecars =
              Streams.concat(
                      firstHalfOfSidecarsWithGaps.stream(),
                      secondHalfSidecars.stream().filter(Optional::isPresent).map(Optional::get))
                  .toList();
          final SafeFuture<Optional<List<Blob>>> maybeCryptoReconstructedBlobs =
              cryptoBlobReconstructor.reconstructBlobs(
                  slotAndBlockRoot, existingSidecars, blobIndices);

          return maybeCryptoReconstructedBlobs.thenCompose(
              maybeBlobs -> {
                if (maybeBlobs.isEmpty()) {
                  return reconstructBlobSidecarsUsingNetworkReconstructor(
                      firstHalfOfSidecarsWithGaps, slotAndBlockRoot, blobIndices);
                } else {
                  LOG.debug(
                      "Blobs {} reconstructed for {} with crypto reconstruction",
                      blobIndices,
                      slotAndBlockRoot);
                  return SafeFuture.completedFuture(maybeBlobs.get());
                }
              });
        });
  }

  private SafeFuture<List<Blob>> reconstructBlobSidecarsUsingNetworkReconstructor(
      final List<DataColumnSidecar> firstHalfOfSidecarsWithGaps,
      final SlotAndBlockRoot slotAndBlockRoot,
      final List<UInt64> blobIndices) {
    LOG.trace("Trying to reconstruct blobs with network reconstructor for {}", slotAndBlockRoot);
    final SafeFuture<Optional<List<Blob>>> maybeNetworkReconstructedBlobs =
        networkBlobReconstructor.reconstructBlobs(
            slotAndBlockRoot, firstHalfOfSidecarsWithGaps, blobIndices);

    return maybeNetworkReconstructedBlobs.thenCompose(
        maybeBlobs -> {
          if (maybeBlobs.isEmpty()) {
            LOG.debug(
                "Blobs {} were not reconstructed for {} with network reconstruction",
                blobIndices,
                slotAndBlockRoot);
            return SafeFuture.completedFuture(Collections.emptyList());
          } else {
            LOG.debug(
                "Blobs {} reconstructed for {} with network reconstruction",
                blobIndices,
                slotAndBlockRoot);
            return SafeFuture.completedFuture(maybeBlobs.get());
          }
        });
  }
}
