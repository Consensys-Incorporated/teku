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

package tech.pegasys.teku.spec.config.builder;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.BlobScheduleEntry;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.config.SpecConfigFuluImpl;

public class FuluBuilder extends BaseForkBuilder
    implements ForkConfigBuilder<SpecConfigElectra, SpecConfigFulu> {
  private static final Logger LOG = LogManager.getLogger();
  private UInt64 fieldElementsPerCell;
  private UInt64 fieldElementsPerExtBlob;
  private UInt64 kzgCommitmentsInclusionProofDepth;
  private Integer cellsPerExtBlob;
  private Integer numberOfColumns;
  private Integer numberOfCustodyGroups;
  private Integer dataColumnSidecarSubnetCount;
  private Integer custodyRequirement;
  private Integer validatorCustodyRequirement;
  private Integer samplesPerSlot;
  private Integer minEpochsForDataColumnSidecarsRequests;
  private Integer maxRequestDataColumnSidecars;
  private UInt64 balancePerAdditionalCustodyGroup;
  private final List<BlobScheduleEntry> blobSchedule = new ArrayList<>();

  FuluBuilder() {}

  @Override
  public SpecConfigAndParent<SpecConfigFulu> build(
      final SpecConfigAndParent<SpecConfigElectra> specConfigAndParent) {
    if (numberOfColumns != null) {
      final Integer newMaxRequestDataColumnSidecars =
          computeMaxRequestDataColumnSidecars(
              specConfigAndParent.specConfig().getMaxRequestBlocksDeneb());
      LOG.debug(
          "Setting maxRequestDataColumnSidecars to {} (was {})",
          newMaxRequestDataColumnSidecars,
          maxRequestDataColumnSidecars);
      maxRequestDataColumnSidecars = newMaxRequestDataColumnSidecars;
    }
    return SpecConfigAndParent.of(
        new SpecConfigFuluImpl(
            specConfigAndParent.specConfig(),
            fieldElementsPerCell,
            fieldElementsPerExtBlob,
            kzgCommitmentsInclusionProofDepth,
            cellsPerExtBlob,
            numberOfColumns,
            numberOfCustodyGroups,
            dataColumnSidecarSubnetCount,
            custodyRequirement,
            validatorCustodyRequirement,
            samplesPerSlot,
            minEpochsForDataColumnSidecarsRequests,
            maxRequestDataColumnSidecars,
            balancePerAdditionalCustodyGroup,
            blobSchedule),
        specConfigAndParent);
  }

  public FuluBuilder fieldElementsPerCell(final UInt64 fieldElementsPerCell) {
    checkNotNull(fieldElementsPerCell);
    this.fieldElementsPerCell = fieldElementsPerCell;
    return this;
  }

  public FuluBuilder fieldElementsPerExtBlob(final UInt64 fieldElementsPerExtBlob) {
    checkNotNull(fieldElementsPerExtBlob);
    this.fieldElementsPerExtBlob = fieldElementsPerExtBlob;
    return this;
  }

  public FuluBuilder cellsPerExtBlob(final Integer cellsPerExtBlob) {
    checkNotNull(cellsPerExtBlob);
    this.cellsPerExtBlob = cellsPerExtBlob;
    return this;
  }

  public FuluBuilder numberOfColumns(final Integer numberOfColumns) {
    checkNotNull(numberOfColumns);
    this.numberOfColumns = numberOfColumns;
    return this;
  }

  public FuluBuilder kzgCommitmentsInclusionProofDepth(
      final UInt64 kzgCommitmentsInclusionProofDepth) {
    checkNotNull(kzgCommitmentsInclusionProofDepth);
    this.kzgCommitmentsInclusionProofDepth = kzgCommitmentsInclusionProofDepth;
    return this;
  }

  public FuluBuilder blobSchedule(final List<BlobScheduleEntry> blobSchedule) {
    checkNotNull(blobSchedule);
    verifyBlobSchedule(blobSchedule);
    this.blobSchedule.clear();
    blobSchedule.stream()
        .sorted(Comparator.comparing(BlobScheduleEntry::epoch))
        .forEach(this.blobSchedule::add);
    return this;
  }

  private void verifyBlobSchedule(final List<BlobScheduleEntry> blobSchedule) {
    final Set<UInt64> seenEpochs = new HashSet<>();
    for (final BlobScheduleEntry entry : blobSchedule) {
      if (!seenEpochs.add(entry.epoch())) {
        throw new IllegalArgumentException(
            String.format(
                "There are duplicate entries for epoch %s in blob schedule.", entry.epoch()));
      }
    }
  }

  public FuluBuilder numberOfCustodyGroups(final Integer numberOfCustodyGroups) {
    checkNotNull(numberOfCustodyGroups);
    this.numberOfCustodyGroups = numberOfCustodyGroups;
    return this;
  }

  public FuluBuilder dataColumnSidecarSubnetCount(final Integer dataColumnSidecarSubnetCount) {
    checkNotNull(dataColumnSidecarSubnetCount);
    this.dataColumnSidecarSubnetCount = dataColumnSidecarSubnetCount;
    return this;
  }

  public FuluBuilder custodyRequirement(final Integer custodyRequirement) {
    checkNotNull(custodyRequirement);
    this.custodyRequirement = custodyRequirement;
    return this;
  }

  public FuluBuilder validatorCustodyRequirement(final Integer validatorCustodyRequirement) {
    checkNotNull(validatorCustodyRequirement);
    this.validatorCustodyRequirement = validatorCustodyRequirement;
    return this;
  }

  public FuluBuilder samplesPerSlot(final Integer samplesPerSlot) {
    checkNotNull(samplesPerSlot);
    this.samplesPerSlot = samplesPerSlot;
    return this;
  }

  public FuluBuilder minEpochsForDataColumnSidecarsRequests(
      final Integer minEpochsForDataColumnSidecarsRequests) {
    checkNotNull(minEpochsForDataColumnSidecarsRequests);
    this.minEpochsForDataColumnSidecarsRequests = minEpochsForDataColumnSidecarsRequests;
    return this;
  }

  @Deprecated
  public FuluBuilder maxRequestDataColumnSidecars(final Integer maxRequestDataColumnSidecars) {
    checkNotNull(maxRequestDataColumnSidecars);
    this.maxRequestDataColumnSidecars = maxRequestDataColumnSidecars;
    return this;
  }

  public FuluBuilder balancePerAdditionalCustodyGroup(
      final UInt64 balancePerAdditionalCustodyGroup) {
    checkNotNull(balancePerAdditionalCustodyGroup);
    this.balancePerAdditionalCustodyGroup = balancePerAdditionalCustodyGroup;
    return this;
  }

  @Override
  public void validate() {
    defaultValuesIfRequired(this);
    validateConstants();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();

    constants.put("cellsPerExtBlob", cellsPerExtBlob);
    constants.put("numberOfColumns", numberOfColumns);
    constants.put("numberOfCustodyGroups", numberOfCustodyGroups);
    constants.put("dataColumnSidecarSubnetCount", dataColumnSidecarSubnetCount);
    constants.put("custodyRequirement", custodyRequirement);
    constants.put("validatorCustodyRequirement", validatorCustodyRequirement);
    constants.put("samplesPerSlot", samplesPerSlot);
    constants.put("fieldElementsPerCell", fieldElementsPerCell);
    constants.put("fieldElementsPerExtBlob", fieldElementsPerExtBlob);
    constants.put("kzgCommitmentsInclusionProofDepth", kzgCommitmentsInclusionProofDepth);
    constants.put("minEpochsForDataColumnSidecarsRequests", minEpochsForDataColumnSidecarsRequests);
    constants.put("balancePerAdditionalCustodyGroup", balancePerAdditionalCustodyGroup);

    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {}

  // compute_max_request_data_column_sidecars
  private Integer computeMaxRequestDataColumnSidecars(final Integer maxRequestBlocksDeneb) {
    return maxRequestBlocksDeneb * numberOfColumns;
  }
}
