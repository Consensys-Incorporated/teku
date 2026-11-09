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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Streams;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.storage.api.DataColumnSidecarNetworkRetriever;

public class NetworkBlobReconstructor extends BlobReconstructor {
  private static final Logger LOG = LogManager.getLogger();
  private final DataColumnSidecarNetworkRetriever networkRetriever;

  public NetworkBlobReconstructor(
      final Spec spec,
      final Supplier<BlobSchema> blobSchemaSupplier,
      final DataColumnSidecarNetworkRetriever networkRetriever) {
    super(spec, blobSchemaSupplier);
    this.networkRetriever = networkRetriever;
  }

  @Override
  public SafeFuture<Optional<List<Blob>>> reconstructBlobs(
      final SlotAndBlockRoot slotAndBlockRoot,
      final List<DataColumnSidecar> existingSidecars,
      final List<UInt64> blobIndices) {
    LOG.trace(
        "Reconstructing blobs from {} sidecars for {}", existingSidecars.size(), slotAndBlockRoot);
    if (!networkRetriever.isEnabled()) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    final Set<DataColumnSlotAndIdentifier> existingSidecarIdentifiers =
        existingSidecars.stream()
            .map(DataColumnSlotAndIdentifier::fromDataColumn)
            .collect(Collectors.toUnmodifiableSet());

    final int halfColumns = spec.getNumberOfDataColumns().orElseThrow() / 2;

    final List<DataColumnSlotAndIdentifier> missingSidecars =
        Stream.iterate(
                UInt64.ZERO,
                // We need the first 50% for reconstruction
                index -> index.isLessThan(halfColumns),
                UInt64::increment)
            .map(
                index ->
                    new DataColumnSlotAndIdentifier(
                        slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), index))
            .filter(idx -> !existingSidecarIdentifiers.contains(idx))
            .toList();
    LOG.trace(
        "Trying to fetch {} missing sidecars for {}", missingSidecars.size(), slotAndBlockRoot);

    return networkRetriever
        .retrieveDataColumnSidecars(missingSidecars)
        .thenApply(
            retrievedDataColumnSidecars -> {
              if (missingSidecars.size() != retrievedDataColumnSidecars.size()) {
                // Not able to retrieve all requested columns
                return Optional.empty();
              }
              final List<DataColumnSidecar> firstHalfColumns =
                  Streams.concat(retrievedDataColumnSidecars.stream(), existingSidecars.stream())
                      .sorted(Comparator.comparing(DataColumnSidecar::getIndex))
                      .toList();
              checkState(firstHalfColumns.size() == halfColumns, "Wrong number of columns");
              return Optional.of(
                  reconstructBlobsFromFirstHalfDataColumns(
                      firstHalfColumns, blobIndices, blobSchemaSupplier.get()));
            });
  }
}
