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
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;

public class SpecConfigFuluImpl extends DelegatingSpecConfigElectra implements SpecConfigFulu {
  private final int cellsPerExtBlob;
  private final int numberOfColumns;
  private final int numberOfCustodyGroups;
  private final int dataColumnSidecarSubnetCount;
  private final int custodyRequirement;
  private final int validatorCustodyRequirement;
  private final int samplesPerSlot;
  private final UInt64 fieldElementsPerCell;
  private final UInt64 fieldElementsPerExtBlob;
  private final UInt64 kzgCommitmentsInclusionProofDepth;
  private final int minEpochsForDataColumnSidecarsRequests;
  private final int maxRequestDataColumnSidecars;
  private final UInt64 balancePerAdditionalCustodyGroup;
  private final List<BlobScheduleEntry> blobSchedule;

  public SpecConfigFuluImpl(
      final SpecConfigElectra specConfig,
      final UInt64 fieldElementsPerCell,
      final UInt64 fieldElementsPerExtBlob,
      final UInt64 kzgCommitmentsInclusionProofDepth,
      final int cellsPerExtBlob,
      final int numberOfColumns,
      final int numberOfCustodyGroups,
      final int dataColumnSidecarSubnetCount,
      final int custodyRequirement,
      final int validatorCustodyRequirement,
      final int samplesPerSlot,
      final int minEpochsForDataColumnSidecarsRequests,
      final int maxRequestDataColumnSidecars,
      final UInt64 balancePerAdditionalCustodyGroup,
      final List<BlobScheduleEntry> blobSchedule) {
    super(specConfig);
    this.fieldElementsPerCell = fieldElementsPerCell;
    this.fieldElementsPerExtBlob = fieldElementsPerExtBlob;
    this.kzgCommitmentsInclusionProofDepth = kzgCommitmentsInclusionProofDepth;
    this.cellsPerExtBlob = cellsPerExtBlob;
    this.numberOfColumns = numberOfColumns;
    this.numberOfCustodyGroups = numberOfCustodyGroups;
    this.dataColumnSidecarSubnetCount = dataColumnSidecarSubnetCount;
    this.custodyRequirement = custodyRequirement;
    this.validatorCustodyRequirement = validatorCustodyRequirement;
    this.samplesPerSlot = samplesPerSlot;
    this.minEpochsForDataColumnSidecarsRequests = minEpochsForDataColumnSidecarsRequests;
    this.maxRequestDataColumnSidecars = maxRequestDataColumnSidecars;
    this.balancePerAdditionalCustodyGroup = balancePerAdditionalCustodyGroup;
    this.blobSchedule = blobSchedule;
  }

  @Override
  public UInt64 getFieldElementsPerCell() {
    return fieldElementsPerCell;
  }

  @Override
  public UInt64 getFieldElementsPerExtBlob() {
    return fieldElementsPerExtBlob;
  }

  @Override
  public int getCellsPerExtBlob() {
    return cellsPerExtBlob;
  }

  @Override
  public int getNumberOfColumns() {
    return numberOfColumns;
  }

  @Override
  public List<BlobScheduleEntry> getBlobSchedule() {
    return blobSchedule;
  }

  @Override
  public UInt64 getKzgCommitmentsInclusionProofDepth() {
    return kzgCommitmentsInclusionProofDepth;
  }

  @Override
  public int getNumberOfCustodyGroups() {
    return numberOfCustodyGroups;
  }

  @Override
  public int getDataColumnSidecarSubnetCount() {
    return dataColumnSidecarSubnetCount;
  }

  @Override
  public int getCustodyRequirement() {
    return custodyRequirement;
  }

  @Override
  public int getValidatorCustodyRequirement() {
    return validatorCustodyRequirement;
  }

  @Override
  public int getSamplesPerSlot() {
    return samplesPerSlot;
  }

  @Override
  public int getMinEpochsForDataColumnSidecarsRequests() {
    return minEpochsForDataColumnSidecarsRequests;
  }

  @Override
  public int getMaxRequestDataColumnSidecars() {
    return maxRequestDataColumnSidecars;
  }

  @Override
  public SpecMilestone getMilestone() {
    return SpecMilestone.FULU;
  }

  @Override
  public UInt64 getBalancePerAdditionalCustodyGroup() {
    return balancePerAdditionalCustodyGroup;
  }

  @Override
  public Optional<SpecConfigFulu> toVersionFulu() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SpecConfigFuluImpl that = (SpecConfigFuluImpl) o;
    return Objects.equals(specConfig, that.specConfig)
        && Objects.equals(fieldElementsPerCell, that.fieldElementsPerCell)
        && Objects.equals(fieldElementsPerExtBlob, that.fieldElementsPerExtBlob)
        && Objects.equals(kzgCommitmentsInclusionProofDepth, that.kzgCommitmentsInclusionProofDepth)
        && Objects.equals(balancePerAdditionalCustodyGroup, that.balancePerAdditionalCustodyGroup)
        && Objects.equals(blobSchedule, that.blobSchedule)
        && cellsPerExtBlob == that.cellsPerExtBlob
        && numberOfColumns == that.numberOfColumns
        && numberOfCustodyGroups == that.numberOfCustodyGroups
        && dataColumnSidecarSubnetCount == that.dataColumnSidecarSubnetCount
        && custodyRequirement == that.custodyRequirement
        && minEpochsForDataColumnSidecarsRequests == that.minEpochsForDataColumnSidecarsRequests
        && maxRequestDataColumnSidecars == that.maxRequestDataColumnSidecars
        && validatorCustodyRequirement == that.validatorCustodyRequirement
        && samplesPerSlot == that.samplesPerSlot;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
        cellsPerExtBlob,
        numberOfColumns,
        numberOfCustodyGroups,
        dataColumnSidecarSubnetCount,
        custodyRequirement,
        fieldElementsPerCell,
        fieldElementsPerExtBlob,
        kzgCommitmentsInclusionProofDepth,
        minEpochsForDataColumnSidecarsRequests,
        maxRequestDataColumnSidecars,
        validatorCustodyRequirement,
        samplesPerSlot,
        balancePerAdditionalCustodyGroup,
        blobSchedule);
  }
}
