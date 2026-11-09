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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.common;

import java.util.Locale;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;

public enum BlockBodyFields implements SszFieldName {
  RANDAO_REVEAL,
  ETH1_DATA,
  GRAFFITI,
  PROPOSER_SLASHINGS,
  ATTESTER_SLASHINGS,
  ATTESTATIONS,
  DEPOSITS,
  VOLUNTARY_EXITS,
  SYNC_AGGREGATE,
  EXECUTION_PAYLOAD,
  EXECUTION_PAYLOAD_HEADER,
  BLS_TO_EXECUTION_CHANGES,
  BLOB_KZG_COMMITMENTS,
  EXECUTION_REQUESTS,
  SIGNED_EXECUTION_PAYLOAD_BID,
  PAYLOAD_ATTESTATIONS,
  PARENT_EXECUTION_REQUESTS;

  private final String sszFieldName;

  BlockBodyFields() {
    this.sszFieldName = name().toLowerCase(Locale.ROOT);
  }

  @Override
  public String getSszFieldName() {
    return sszFieldName;
  }
}
