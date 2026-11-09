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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.AttesterSlashingValidator.AttesterSlashingInvalidReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttesterSlashingValidatorTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final Spec mockSpec = mock(Spec.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private AttesterSlashingValidator attesterSlashingValidator;
  private final BeaconState bestState = dataStructureUtil.randomBeaconState();

  @BeforeEach
  void beforeEach() {
    when(recentChainData.getBestState())
        .thenReturn(Optional.of(SafeFuture.completedFuture(bestState)));
    attesterSlashingValidator = new AttesterSlashingValidator(recentChainData, mockSpec);
  }

  @Test
  public void shouldAcceptValidAttesterSlashing() {
    AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(mockSpec.validateAttesterSlashing(bestState, slashing)).thenReturn(Optional.empty());
    assertThat(attesterSlashingValidator.validateForGossip(slashing))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  @Test
  public void shouldRejectInvalidAttesterSlashing() {
    AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(mockSpec.validateAttesterSlashing(bestState, slashing))
        .thenReturn(Optional.of(AttesterSlashingInvalidReason.ATTESTATIONS_NOT_SLASHABLE));
    assertThat(attesterSlashingValidator.validateForGossip(slashing))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  public void shouldIgnoreAttesterSlashingForTheSameAttesters() {
    AttesterSlashing slashing1 = dataStructureUtil.randomAttesterSlashing();
    AttesterSlashing slashing2 =
        slashing1.getSchema().create(slashing1.getAttestation1(), slashing1.getAttestation2());
    when(mockSpec.validateAttesterSlashing(eq(bestState), any())).thenReturn(Optional.empty());
    assertThat(attesterSlashingValidator.validateForGossip(slashing1))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    assertThat(attesterSlashingValidator.validateForGossip(slashing2))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }
}
