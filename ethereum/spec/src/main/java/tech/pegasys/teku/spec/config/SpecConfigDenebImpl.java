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

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.spec.SpecMilestone;

public class SpecConfigDenebImpl extends DelegatingSpecConfigCapella implements SpecConfigDeneb {

  private final int maxPerEpochActivationChurnLimit;
  private final int fieldElementsPerBlob;
  private final int maxBlobCommitmentsPerBlock;
  private final int maxBlobsPerBlock;
  private final int kzgCommitmentInclusionProofDepth;
  private final int maxRequestBlocksDeneb;
  private final int maxRequestBlobSidecars;
  private final int minEpochsForBlobSidecarsRequests;
  private final int blobSidecarSubnetCount;
  private final Optional<Integer> maybeEpochsStoreBlobs;

  public SpecConfigDenebImpl(
      final SpecConfigCapella specConfig,
      final int maxPerEpochActivationChurnLimit,
      final int fieldElementsPerBlob,
      final int maxBlobCommitmentsPerBlock,
      final int maxBlobsPerBlock,
      final int kzgCommitmentInclusionProofDepth,
      final int maxRequestBlocksDeneb,
      final int maxRequestBlobSidecars,
      final int minEpochsForBlobSidecarsRequests,
      final int blobSidecarSubnetCount,
      final Optional<Integer> maybeEpochsStoreBlobs) {
    super(specConfig);
    this.maxPerEpochActivationChurnLimit = maxPerEpochActivationChurnLimit;
    this.fieldElementsPerBlob = fieldElementsPerBlob;
    this.maxBlobCommitmentsPerBlock = maxBlobCommitmentsPerBlock;
    this.maxBlobsPerBlock = maxBlobsPerBlock;
    this.kzgCommitmentInclusionProofDepth = kzgCommitmentInclusionProofDepth;
    this.maxRequestBlocksDeneb = maxRequestBlocksDeneb;
    this.maxRequestBlobSidecars = maxRequestBlobSidecars;
    this.minEpochsForBlobSidecarsRequests = minEpochsForBlobSidecarsRequests;
    this.blobSidecarSubnetCount = blobSidecarSubnetCount;
    this.maybeEpochsStoreBlobs = maybeEpochsStoreBlobs;
  }

  @Override
  public int getMaxPerEpochActivationChurnLimit() {
    return maxPerEpochActivationChurnLimit;
  }

  @Override
  public int getFieldElementsPerBlob() {
    return fieldElementsPerBlob;
  }

  @Override
  public int getMaxBlobCommitmentsPerBlock() {
    return maxBlobCommitmentsPerBlock;
  }

  @Override
  public int getMaxBlobsPerBlock() {
    return maxBlobsPerBlock;
  }

  @Override
  public int getKzgCommitmentInclusionProofDepth() {
    return kzgCommitmentInclusionProofDepth;
  }

  @Override
  public int getMaxRequestBlocksDeneb() {
    return maxRequestBlocksDeneb;
  }

  @Override
  public int getMaxRequestBlobSidecars() {
    return maxRequestBlobSidecars;
  }

  @Override
  public int getMinEpochsForBlobSidecarsRequests() {
    return minEpochsForBlobSidecarsRequests;
  }

  @Override
  public int getBlobSidecarSubnetCount() {
    return blobSidecarSubnetCount;
  }

  @Override
  public int getEpochsStoreBlobs() {
    return maybeEpochsStoreBlobs
        .filter(epochsStoreBlobsInput -> epochsStoreBlobsInput > minEpochsForBlobSidecarsRequests)
        .orElse(minEpochsForBlobSidecarsRequests);
  }

  @Override
  public Optional<SpecConfigDeneb> toVersionDeneb() {
    return Optional.of(this);
  }

  @Override
  public SpecMilestone getMilestone() {
    return SpecMilestone.DENEB;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SpecConfigDenebImpl that = (SpecConfigDenebImpl) o;
    return Objects.equals(specConfig, that.specConfig)
        && Objects.equals(maybeEpochsStoreBlobs, that.maybeEpochsStoreBlobs)
        && fieldElementsPerBlob == that.fieldElementsPerBlob
        && maxBlobCommitmentsPerBlock == that.maxBlobCommitmentsPerBlock
        && maxBlobsPerBlock == that.maxBlobsPerBlock
        && kzgCommitmentInclusionProofDepth == that.kzgCommitmentInclusionProofDepth
        && maxRequestBlocksDeneb == that.maxRequestBlocksDeneb
        && maxRequestBlobSidecars == that.maxRequestBlobSidecars
        && minEpochsForBlobSidecarsRequests == that.minEpochsForBlobSidecarsRequests
        && blobSidecarSubnetCount == that.blobSidecarSubnetCount;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
        fieldElementsPerBlob,
        maxBlobCommitmentsPerBlock,
        maxBlobsPerBlock,
        kzgCommitmentInclusionProofDepth,
        maxRequestBlocksDeneb,
        maxRequestBlobSidecars,
        minEpochsForBlobSidecarsRequests,
        blobSidecarSubnetCount,
        maybeEpochsStoreBlobs);
  }
}
