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

package tech.pegasys.teku.statetransition.validation;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;

public interface BlockBroadcastValidator {

  BlockBroadcastValidator NOOP =
      new BlockBroadcastValidator() {
        private static final SafeFuture<BroadcastValidationResult> SUCCESS =
            SafeFuture.completedFuture(BroadcastValidationResult.SUCCESS);

        @Override
        public void onConsensusValidationSucceeded() {}

        @Override
        public void attachToBlockImport(final SafeFuture<BlockImportResult> blockImportResult) {}

        @Override
        public SafeFuture<BroadcastValidationResult> getResult() {
          return SUCCESS;
        }
      };

  static BlockBroadcastValidator create(
      final SignedBeaconBlock block,
      final BlockGossipValidator blockGossipValidator,
      final BroadcastValidationLevel broadcastValidationLevel) {
    if (broadcastValidationLevel == BroadcastValidationLevel.NOT_REQUIRED) {
      return NOOP;
    }
    return BlockBroadcastValidatorImpl.create(
        block, blockGossipValidator, broadcastValidationLevel);
  }

  void onConsensusValidationSucceeded();

  void attachToBlockImport(SafeFuture<BlockImportResult> blockImportResult);

  SafeFuture<BroadcastValidationResult> getResult();

  enum BroadcastValidationResult {
    SUCCESS,
    GOSSIP_FAILURE,
    CONSENSUS_FAILURE,
    EQUIVOCATION_FAILURE;

    public boolean isFailure() {
      return this != SUCCESS;
    }
  }
}
