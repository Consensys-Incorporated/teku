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

package tech.pegasys.teku.services.beaconchain;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.dataproviders.lookup.SingleBlockProvider;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class SingleBlockProviderResolver implements SingleBlockProvider {

  private final SingleBlockProvider blockProviderFallback;
  private final SingleBlockProvider blockProvider;

  public SingleBlockProviderResolver(
      final SingleBlockProvider blockProviderFallback, final SingleBlockProvider blockProvider) {
    this.blockProviderFallback = blockProviderFallback;
    this.blockProvider = blockProvider;
  }

  @Override
  public Optional<SignedBeaconBlock> getBlock(final Bytes32 blockRoot) {
    return blockProvider.getBlock(blockRoot).or(() -> blockProviderFallback.getBlock(blockRoot));
  }
}
