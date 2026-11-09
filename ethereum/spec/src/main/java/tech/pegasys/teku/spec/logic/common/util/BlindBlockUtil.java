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

package tech.pegasys.teku.spec.logic.common.util;

import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockBlinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;

public abstract class BlindBlockUtil {

  public SafeFuture<Optional<SignedBeaconBlock>> unblindSignedBeaconBlock(
      final SignedBeaconBlock signedBlindedBeaconBlock,
      final Consumer<SignedBeaconBlockUnblinder> beaconBlockUnblinderConsumer) {
    final SignedBeaconBlockUnblinder beaconBlockUnblinder =
        createSignedBeaconBlockUnblinder(signedBlindedBeaconBlock);
    beaconBlockUnblinderConsumer.accept(beaconBlockUnblinder);
    return beaconBlockUnblinder.unblind();
  }

  public SignedBeaconBlock blindSignedBeaconBlock(final SignedBeaconBlock signedBeaconBlock) {
    return getSignedBeaconBlockBlinder().blind(signedBeaconBlock);
  }

  protected abstract SignedBeaconBlockUnblinder createSignedBeaconBlockUnblinder(
      final SignedBeaconBlock signedBlindedBeaconBlock);

  protected abstract SignedBeaconBlockBlinder getSignedBeaconBlockBlinder();
}
