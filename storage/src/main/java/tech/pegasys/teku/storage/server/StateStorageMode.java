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

package tech.pegasys.teku.storage.server;

public enum StateStorageMode {

  // All historical state is available to query in archive mode
  ARCHIVE,
  // No historical state is available to query in mode "prune"
  PRUNE,
  // No historical state and blocks beyond the minimum retention period are removed
  MINIMAL,
  NOT_SET;

  public static final StateStorageMode DEFAULT_MODE = NOT_SET;

  public boolean storesFinalizedStates() {
    return this == ARCHIVE;
  }

  public boolean storesAllBlocks() {
    return this != MINIMAL;
  }
}
