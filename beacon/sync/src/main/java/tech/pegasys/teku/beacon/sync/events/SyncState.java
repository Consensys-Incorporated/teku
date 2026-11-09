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

package tech.pegasys.teku.beacon.sync.events;

public enum SyncState {
  START_UP,
  SYNCING,
  OPTIMISTIC_SYNCING,
  AWAITING_EL, // Beacon chain has completed optimistic sync but waiting for the EL
  IN_SYNC;

  public boolean isInSync() {
    return this == IN_SYNC;
  }

  public boolean isSyncing() {
    return this == SYNCING || this == OPTIMISTIC_SYNCING || this == AWAITING_EL;
  }

  public boolean isOptimistic() {
    return this == OPTIMISTIC_SYNCING || this == AWAITING_EL;
  }
}
