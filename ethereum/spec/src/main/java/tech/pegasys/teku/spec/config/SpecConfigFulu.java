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

package tech.pegasys.teku.spec.config;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface SpecConfigFulu extends SpecConfigElectra {

  static SpecConfigFulu required(final SpecConfig specConfig) {
    return specConfig
        .toVersionFulu()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Fulu spec config but got: " + specConfig.getClass().getSimpleName()));
  }

  UInt64 getFieldElementsPerCell();

  UInt64 getFieldElementsPerExtBlob();

  int getCellsPerExtBlob();

  int getNumberOfColumns();

  List<BlobScheduleEntry> getBlobSchedule();

  /** DataColumnSidecar's */
  UInt64 getKzgCommitmentsInclusionProofDepth();

  int getNumberOfCustodyGroups();

  // networking
  int getDataColumnSidecarSubnetCount();

  int getCustodyRequirement();

  int getValidatorCustodyRequirement();

  int getSamplesPerSlot();

  int getMinEpochsForDataColumnSidecarsRequests();

  int getMaxRequestDataColumnSidecars();

  UInt64 getBalancePerAdditionalCustodyGroup();

  @Override
  Optional<SpecConfigFulu> toVersionFulu();
}
