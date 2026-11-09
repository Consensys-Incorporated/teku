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

package tech.pegasys.teku.validator.client.duties.attestations;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.generator.signatures.NoOpRemoteSigner.NO_OP_REMOTE_SIGNER;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDutyAggregators.CommitteeAggregator;

abstract class AggregationDutyAggregatorsTest {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  protected AggregationDutyAggregators aggregationDutyAggregators;

  @BeforeEach
  public void setUp() {
    aggregationDutyAggregators = getAggregator();
  }

  @Test
  public void addValidatorsAndStreaming() {
    final CommitteeAggregator committeeAggregator1 = randomCommitteeAggregator();
    final CommitteeAggregator committeeAggregator2 = randomCommitteeAggregator();
    final CommitteeAggregator committeeAggregator3 = randomCommitteeAggregator();

    addValidator(committeeAggregator1);
    addValidator(committeeAggregator2);
    addValidator(committeeAggregator3);

    assertThat(aggregationDutyAggregators.hasAggregators()).isTrue();
    assertThat(aggregationDutyAggregators.streamAggregators())
        .contains(committeeAggregator1, committeeAggregator2, committeeAggregator3);
  }

  @Test
  public void hasAggregatorsReturnFalseWhenNoAggregatorsHaveBeenAdded() {
    assertThat(aggregationDutyAggregators.hasAggregators()).isFalse();
  }

  abstract AggregationDutyAggregators getAggregator();

  protected void addValidator(final CommitteeAggregator committeeAggregator) {
    aggregationDutyAggregators.addValidator(
        committeeAggregator.validator(),
        committeeAggregator.validatorIndex().intValue(),
        committeeAggregator.proof(),
        committeeAggregator.attestationCommitteeIndex().intValue(),
        committeeAggregator.unsignedAttestationFuture());
  }

  protected CommitteeAggregator randomCommitteeAggregator() {
    return randomCommitteeAggregator(dataStructureUtil.randomPositiveInt());
  }

  protected CommitteeAggregator randomCommitteeAggregator(final int committeeIndex) {
    final BLSKeyPair keyPair = dataStructureUtil.randomKeyPair();
    final Validator validator =
        new Validator(keyPair.getPublicKey(), NO_OP_REMOTE_SIGNER, Optional::empty, true);
    final UInt64 validatorIndex = dataStructureUtil.randomValidatorIndex();

    return new CommitteeAggregator(
        validator,
        validatorIndex,
        UInt64.valueOf(committeeIndex),
        dataStructureUtil.randomSignature(),
        SafeFuture.completedFuture(Optional.of(dataStructureUtil.randomAttestationData())));
  }
}
