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

import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.ValidatorIsConnectedProvider;

public class ValidatorIsConnectedProviderReference implements ValidatorIsConnectedProvider {
  private final Supplier<ValidatorIsConnectedProvider> validatorIsConnectedProvider;

  public ValidatorIsConnectedProviderReference(
      final Supplier<ValidatorIsConnectedProvider> validatorIsConnectedProvider) {
    this.validatorIsConnectedProvider = validatorIsConnectedProvider;
  }

  @Override
  public boolean isValidatorConnected(final int validatorId, final UInt64 slot) {
    return validatorIsConnectedProvider.get().isValidatorConnected(validatorId, slot);
  }

  @Override
  public SafeFuture<Boolean> isBlockProposerConnected(final UInt64 slot) {
    return validatorIsConnectedProvider.get().isBlockProposerConnected(slot);
  }
}
