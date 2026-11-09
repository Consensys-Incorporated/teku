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

import java.util.Optional;

public class DelegatingSpecConfigDeneb extends DelegatingSpecConfigCapella
    implements SpecConfigDeneb {
  private final SpecConfigDeneb specConfigDeneb;

  public DelegatingSpecConfigDeneb(final SpecConfigDeneb specConfig) {
    super(specConfig);
    this.specConfigDeneb = SpecConfigDeneb.required(specConfig);
  }

  @Override
  public Optional<SpecConfigDeneb> toVersionDeneb() {
    return Optional.of(this);
  }

  @Override
  public int getMaxPerEpochActivationChurnLimit() {
    return specConfigDeneb.getMaxPerEpochActivationChurnLimit();
  }

  @Override
  public int getFieldElementsPerBlob() {
    return specConfigDeneb.getFieldElementsPerBlob();
  }

  @Override
  public int getMaxBlobCommitmentsPerBlock() {
    return specConfigDeneb.getMaxBlobCommitmentsPerBlock();
  }

  @Override
  public int getMaxBlobsPerBlock() {
    return specConfigDeneb.getMaxBlobsPerBlock();
  }

  @Override
  public int getDenebMaxBlobsPerBlock() {
    return specConfigDeneb.getDenebMaxBlobsPerBlock();
  }

  @Override
  public int getKzgCommitmentInclusionProofDepth() {
    return specConfigDeneb.getKzgCommitmentInclusionProofDepth();
  }

  @Override
  public int getEpochsStoreBlobs() {
    return specConfigDeneb.getEpochsStoreBlobs();
  }

  @Override
  public int getMaxRequestBlocksDeneb() {
    return specConfigDeneb.getMaxRequestBlocksDeneb();
  }

  @Override
  public int getMaxRequestBlobSidecars() {
    return specConfigDeneb.getMaxRequestBlobSidecars();
  }

  @Override
  public int getMinEpochsForBlobSidecarsRequests() {
    return specConfigDeneb.getMinEpochsForBlobSidecarsRequests();
  }

  @Override
  public int getBlobSidecarSubnetCount() {
    return specConfigDeneb.getBlobSidecarSubnetCount();
  }
}
