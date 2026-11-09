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

public class DelegatingSpecConfigFulu extends DelegatingSpecConfigElectra
    implements SpecConfigFulu {
  private final SpecConfigFulu delegate;

  public DelegatingSpecConfigFulu(final SpecConfigFulu specConfig) {
    super(specConfig);
    this.delegate = SpecConfigFulu.required(specConfig);
  }

  @Override
  public Optional<SpecConfigFulu> toVersionFulu() {
    return Optional.of(this);
  }

  @Override
  public UInt64 getFieldElementsPerCell() {
    return delegate.getFieldElementsPerCell();
  }

  @Override
  public UInt64 getFieldElementsPerExtBlob() {
    return delegate.getFieldElementsPerExtBlob();
  }

  @Override
  public int getCellsPerExtBlob() {
    return delegate.getCellsPerExtBlob();
  }

  @Override
  public int getNumberOfColumns() {
    return delegate.getNumberOfColumns();
  }

  @Override
  public List<BlobScheduleEntry> getBlobSchedule() {
    return delegate.getBlobSchedule();
  }

  @Override
  public UInt64 getKzgCommitmentsInclusionProofDepth() {
    return delegate.getKzgCommitmentsInclusionProofDepth();
  }

  @Override
  public int getNumberOfCustodyGroups() {
    return delegate.getNumberOfCustodyGroups();
  }

  @Override
  public int getDataColumnSidecarSubnetCount() {
    return delegate.getDataColumnSidecarSubnetCount();
  }

  @Override
  public int getCustodyRequirement() {
    return delegate.getCustodyRequirement();
  }

  @Override
  public int getValidatorCustodyRequirement() {
    return delegate.getValidatorCustodyRequirement();
  }

  @Override
  public int getSamplesPerSlot() {
    return delegate.getSamplesPerSlot();
  }

  @Override
  public int getMinEpochsForDataColumnSidecarsRequests() {
    return delegate.getMinEpochsForDataColumnSidecarsRequests();
  }

  @Override
  public int getMaxRequestDataColumnSidecars() {
    return delegate.getMaxRequestDataColumnSidecars();
  }

  @Override
  public UInt64 getBalancePerAdditionalCustodyGroup() {
    return delegate.getBalancePerAdditionalCustodyGroup();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DelegatingSpecConfigFulu that = (DelegatingSpecConfigFulu) o;
    return Objects.equals(delegate, that.delegate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(delegate);
  }
}
