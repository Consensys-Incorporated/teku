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

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface ValidatorIsConnectedProvider {
  ValidatorIsConnectedProvider ALWAYS =
      new ValidatorIsConnectedProvider() {
        @Override
        public boolean isValidatorConnected(final int validatorId, final UInt64 slot) {
          return true;
        }

        @Override
        public SafeFuture<Boolean> isBlockProposerConnected(final UInt64 slot) {
          return SafeFuture.completedFuture(true);
        }
      };

  boolean isValidatorConnected(int validatorId, UInt64 slot);

  SafeFuture<Boolean> isBlockProposerConnected(UInt64 slot);
}
