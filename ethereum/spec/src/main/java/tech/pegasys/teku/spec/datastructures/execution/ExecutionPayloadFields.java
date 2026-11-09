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

package tech.pegasys.teku.spec.datastructures.execution;

import java.util.Locale;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;

public enum ExecutionPayloadFields implements SszFieldName {
  PARENT_HASH,
  FEE_RECIPIENT,
  STATE_ROOT,
  RECEIPTS_ROOT,
  LOGS_BLOOM,
  PREV_RANDAO,
  BLOCK_NUMBER,
  GAS_LIMIT,
  GAS_USED,
  TIMESTAMP,
  EXTRA_DATA,
  BASE_FEE_PER_GAS,
  BLOCK_HASH,
  TRANSACTIONS,
  WITHDRAWALS,
  TRANSACTIONS_ROOT,
  WITHDRAWALS_ROOT,
  BLOB_GAS_USED,
  EXCESS_BLOB_GAS,
  // Gloas
  BLOCK_ACCESS_LIST,
  BLOCK_ACCESS_LIST_ROOT,
  SLOT_NUMBER,
  PARENT_BLOCK_HASH,
  PARENT_BLOCK_ROOT,
  BUILDER_INDEX,
  SLOT,
  VALUE,
  EXECUTION_PAYMENT,
  BLOB_KZG_COMMITMENTS,
  EXECUTION_REQUESTS_ROOT;

  private final String sszFieldName;

  ExecutionPayloadFields() {
    this.sszFieldName = name().toLowerCase(Locale.ROOT);
  }

  @Override
  public String getSszFieldName() {
    return sszFieldName;
  }
}
