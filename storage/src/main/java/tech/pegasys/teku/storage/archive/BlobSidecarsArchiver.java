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

package tech.pegasys.teku.storage.archive;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

public interface BlobSidecarsArchiver {

  BlobSidecarsArchiver NOOP =
      new BlobSidecarsArchiver() {
        @Override
        public void archive(
            final SlotAndBlockRoot slotAndBlockRoot, final List<BlobSidecar> blobSidecars) {}

        @Override
        public Optional<List<BlobSidecar>> retrieve(final SlotAndBlockRoot slotAndBlockRoot) {
          return Optional.empty();
        }

        @Override
        public Optional<List<BlobSidecar>> retrieve(final UInt64 slot) {
          return Optional.empty();
        }
      };

  void archive(SlotAndBlockRoot slotAndBlockRoot, List<BlobSidecar> blobSidecars);

  Optional<List<BlobSidecar>> retrieve(SlotAndBlockRoot slotAndBlockRoot);

  Optional<List<BlobSidecar>> retrieve(UInt64 slot);
}
