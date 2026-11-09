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

package tech.pegasys.teku.statetransition;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface OperationPool<T extends SszData> {

  String OPERATION_POOL_SIZE_METRIC = "operation_pool_size_";
  String OPERATION_POOL_SIZE_VALIDATION_REASON = "operation_pool_validation_";

  void subscribeOperationAdded(OperationAddedSubscriber<T> subscriber);

  SszList<T> getItemsForBlock(BeaconState stateAtBlockSlot, int maxItemsForBlock);

  SszList<T> getItemsForBlock(
      BeaconState stateAtBlockSlot,
      int maxItemsForBlock,
      Predicate<T> filter,
      Consumer<T> includedItemConsumer);

  SafeFuture<InternalValidationResult> addLocal(T item);

  SafeFuture<InternalValidationResult> addRemote(T item, Optional<UInt64> arrivalTimestamp);

  void addAll(SszCollection<T> items);

  void removeAll(SszCollection<T> items);

  Set<T> getAll();

  default Set<T> getLocallySubmitted() {
    return Set.of();
  }

  @VisibleForTesting
  int size();
}
