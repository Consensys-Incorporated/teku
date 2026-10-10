/*
 * Copyright Consensys Software Inc., 2026
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

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.spec.SpecMilestone;

public class SpecConfigHezeImpl extends DelegatingSpecConfigGloas implements SpecConfigHeze {

  private final int inclusionListDueBps;
  private final int maxRequestInclusionList;
  private final int minSlotsForInclusionListsRequests;
  private final int maxTransactionsBytesPerInclusionList;
  private final int maxSignedInclusionListSize;
  private final int inclusionListCommitteeSize;

  public SpecConfigHezeImpl(
      final SpecConfigGloas specConfig,
      final int inclusionListDueBps,
      final int maxRequestInclusionList,
      final int minSlotsForInclusionListsRequests,
      final int maxTransactionsBytesPerInclusionList,
      final int maxSignedInclusionListSize,
      final int inclusionListCommitteeSize) {
    super(specConfig);
    this.inclusionListDueBps = inclusionListDueBps;
    this.maxRequestInclusionList = maxRequestInclusionList;
    this.minSlotsForInclusionListsRequests = minSlotsForInclusionListsRequests;
    this.maxTransactionsBytesPerInclusionList = maxTransactionsBytesPerInclusionList;
    this.maxSignedInclusionListSize = maxSignedInclusionListSize;
    this.inclusionListCommitteeSize = inclusionListCommitteeSize;
  }

  @Override
  public SpecMilestone getMilestone() {
    return SpecMilestone.HEZE;
  }

  @Override
  public int getInclusionListDueBps() {
    return inclusionListDueBps;
  }

  @Override
  public int getMaxRequestInclusionList() {
    return maxRequestInclusionList;
  }

  @Override
  public int getMinSlotsForInclusionListsRequests() {
    return minSlotsForInclusionListsRequests;
  }

  @Override
  public int getMaxTransactionsBytesPerInclusionList() {
    return maxTransactionsBytesPerInclusionList;
  }

  @Override
  public int getMaxSignedInclusionListSize() {
    return maxSignedInclusionListSize;
  }

  @Override
  public int getInclusionListCommitteeSize() {
    return inclusionListCommitteeSize;
  }

  @Override
  public Optional<SpecConfigHeze> toVersionHeze() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    SpecConfigHezeImpl that = (SpecConfigHezeImpl) o;
    return inclusionListDueBps == that.inclusionListDueBps
        && maxRequestInclusionList == that.maxRequestInclusionList
        && minSlotsForInclusionListsRequests == that.minSlotsForInclusionListsRequests
        && maxTransactionsBytesPerInclusionList == that.maxTransactionsBytesPerInclusionList
        && maxSignedInclusionListSize == that.maxSignedInclusionListSize
        && inclusionListCommitteeSize == that.inclusionListCommitteeSize;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        inclusionListDueBps,
        maxRequestInclusionList,
        minSlotsForInclusionListsRequests,
        maxTransactionsBytesPerInclusionList,
        maxSignedInclusionListSize,
        inclusionListCommitteeSize);
  }
}
