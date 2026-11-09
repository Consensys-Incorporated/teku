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

package tech.pegasys.teku.reference.common.epoch_processing;

public enum EpochOperation {
  PROCESS_SLASHINGS,
  PROCESS_REGISTRY_UPDATES,
  PROCESS_REWARDS_AND_PENALTIES,
  PROCESS_JUSTIFICATION_AND_FINALIZATION,
  PROCESS_EFFECTIVE_BALANCE_UPDATES,
  PROCESS_PARTICIPATION_FLAG_UPDATES,
  PROCESS_SLASHINGS_RESET,
  PROCESS_ETH1_DATA_RESET,
  PROCESS_RANDAO_MIXES_RESET,
  PROCESS_HISTORICAL_ROOTS_UPDATE,
  SYNC_COMMITTEE_UPDATES,
  PROCESS_HISTORICAL_SUMMARIES_UPDATE,
  PENDING_DEPOSITS,
  PENDING_CONSOLIDATIONS,
  INACTIVITY_UPDATES,
  PROPOSER_LOOKAHEAD,
  BUILDER_PENDING_PAYMENTS,
  PTC_WINDOW;
}
