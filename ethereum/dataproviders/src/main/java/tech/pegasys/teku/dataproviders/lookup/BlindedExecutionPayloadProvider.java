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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;

public interface BlindedExecutionPayloadProvider {

  BlindedExecutionPayloadProvider NOOP =
      roots -> SafeFuture.completedFuture(Collections.emptyMap());

  /**
   * Combines multiple providers, querying the primary first and falling back to secondary providers
   * for any missing roots. Use this to combine a hot store provider with a database-backed
   * provider.
   */
  static BlindedExecutionPayloadProvider combined(
      final BlindedExecutionPayloadProvider primaryProvider,
      final BlindedExecutionPayloadProvider... secondaryProviders) {
    return (final Set<Bytes32> blockRoots) -> {
      SafeFuture<Map<Bytes32, SignedBlindedExecutionPayloadEnvelope>> result =
          primaryProvider.getBlindedExecutionPayloads(blockRoots).thenApply(HashMap::new);
      for (BlindedExecutionPayloadProvider nextProvider : secondaryProviders) {
        result =
            result.thenCompose(
                blindedExecutionPayloads -> {
                  final Set<Bytes32> remainingRoots =
                      Sets.difference(blockRoots, blindedExecutionPayloads.keySet());
                  if (remainingRoots.isEmpty()) {
                    return SafeFuture.completedFuture(blindedExecutionPayloads);
                  }
                  return nextProvider
                      .getBlindedExecutionPayloads(remainingRoots)
                      .thenApply(
                          moreBlindedPayloads -> {
                            blindedExecutionPayloads.putAll(moreBlindedPayloads);
                            return blindedExecutionPayloads;
                          });
                });
      }
      return result;
    };
  }

  SafeFuture<Map<Bytes32, SignedBlindedExecutionPayloadEnvelope>> getBlindedExecutionPayloads(
      Set<Bytes32> blockRoots);

  default SafeFuture<Optional<SignedBlindedExecutionPayloadEnvelope>> getBlindedExecutionPayload(
      final Bytes32 blockRoot) {
    return getBlindedExecutionPayloads(Set.of(blockRoot))
        .thenApply(
            blindedExecutionPayloads ->
                Optional.ofNullable(blindedExecutionPayloads.get(blockRoot)));
  }
}
