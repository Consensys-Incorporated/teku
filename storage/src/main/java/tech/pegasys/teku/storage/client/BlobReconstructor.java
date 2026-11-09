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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

public abstract class BlobReconstructor {
  final Spec spec;
  final Supplier<BlobSchema> blobSchemaSupplier;

  public BlobReconstructor(final Spec spec, final Supplier<BlobSchema> blobSchemaSupplier) {
    this.spec = spec;
    this.blobSchemaSupplier = blobSchemaSupplier;
  }

  abstract SafeFuture<Optional<List<Blob>>> reconstructBlobs(
      final SlotAndBlockRoot slotAndBlockRoot,
      final List<DataColumnSidecar> existingSidecars,
      final List<UInt64> blobIndices);

  List<Blob> reconstructBlobsFromFirstHalfDataColumns(
      final List<DataColumnSidecar> dataColumnSidecars,
      final List<UInt64> blobIndices,
      final BlobSchema blobSchema) {
    if (dataColumnSidecars.isEmpty()) {
      return Collections.emptyList();
    }
    return IntStream.range(0, dataColumnSidecars.getFirst().getKzgProofs().size())
        .filter(index -> blobIndices.isEmpty() || blobIndices.contains(UInt64.valueOf(index)))
        .mapToObj(blobIndex -> constructBlob(dataColumnSidecars, blobIndex, blobSchema))
        .toList();
  }

  Blob constructBlob(
      final List<DataColumnSidecar> dataColumnSidecars,
      final int blobIndex,
      final BlobSchema blobSchema) {
    final Bytes blobBytes =
        dataColumnSidecars.stream()
            .map(dataColumnSidecar -> dataColumnSidecar.getColumn().get(blobIndex).getBytes())
            .reduce(Bytes::concatenate)
            .orElseThrow();
    return blobSchema.create(blobBytes);
  }
}
