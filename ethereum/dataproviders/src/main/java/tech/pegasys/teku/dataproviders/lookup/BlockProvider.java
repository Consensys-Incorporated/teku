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

package tech.pegasys.teku.dataproviders.lookup;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

@FunctionalInterface
public interface BlockProvider {

  BlockProvider NOOP = (roots) -> SafeFuture.completedFuture(Collections.emptyMap());

  static BlockProvider fromDynamicMap(final Supplier<Map<Bytes32, SignedBeaconBlock>> mapSupplier) {
    return (roots) -> fromMap(mapSupplier.get()).getBlocks(roots);
  }

  static BlockProvider fromMap(final Map<Bytes32, SignedBeaconBlock> blockMap) {
    return (roots) ->
        SafeFuture.completedFuture(
            roots.stream()
                .filter(blockMap::containsKey)
                .collect(Collectors.toMap(Function.identity(), blockMap::get)));
  }

  static BlockProvider withKnownBlocks(
      final BlockProvider blockProvider, final Map<Bytes32, SignedBeaconBlock> knownBlocks) {
    return combined(fromMap(knownBlocks), blockProvider);
  }

  static BlockProvider combined(
      final BlockProvider primaryProvider, final BlockProvider... secondaryProviders) {

    return (final Set<Bytes32> blockRoots) -> {
      SafeFuture<Map<Bytes32, SignedBeaconBlock>> result = primaryProvider.getBlocks(blockRoots);
      for (BlockProvider nextProvider : secondaryProviders) {
        result =
            result.thenCompose(
                blocks -> {
                  final Set<Bytes32> remainingRoots = Sets.difference(blockRoots, blocks.keySet());
                  if (remainingRoots.isEmpty()) {
                    return SafeFuture.completedFuture(blocks);
                  }
                  return nextProvider
                      .getBlocks(remainingRoots)
                      .thenApply(
                          moreBlocks -> {
                            blocks.putAll(moreBlocks);
                            return blocks;
                          });
                });
      }
      return result;
    };
  }

  SafeFuture<Map<Bytes32, SignedBeaconBlock>> getBlocks(final Set<Bytes32> blockRoots);

  default SafeFuture<Map<Bytes32, SignedBeaconBlock>> getBlocks(final List<Bytes32> blockRoots) {
    return getBlocks(new HashSet<>(blockRoots));
  }

  default SafeFuture<Optional<SignedBeaconBlock>> getBlock(final Bytes32 blockRoot) {
    return getBlocks(Set.of(blockRoot))
        .thenApply(blocks -> Optional.ofNullable(blocks.get(blockRoot)));
  }
}
