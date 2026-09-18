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

package tech.pegasys.teku.validator.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.json.types.validator.PayloadTimelinessCommitteeDuties;
import tech.pegasys.teku.ethereum.json.types.validator.PtcDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.payloadattestations.PayloadAttestationProductionDuty;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

class PtcDutyLoaderTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Validator validator1 =
      new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  private final Validator validator2 =
      new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  private final int validator1Index = 19;
  private final int validator2Index = 23;
  private final IntSet validatorIndices = IntSet.of(validator1Index, validator2Index);
  private final OwnedValidators validators =
      new OwnedValidators(
          Map.of(validator1.getPublicKey(), validator1, validator2.getPublicKey(), validator2));
  private final ValidatorIndexProvider validatorIndexProvider = mock(ValidatorIndexProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  @SuppressWarnings("unchecked")
  private final SlotBasedScheduledDuties<PayloadAttestationProductionDuty, Duty>
      slotBasedScheduledDuties = mock(SlotBasedScheduledDuties.class);

  private final PayloadTimelinessCommitteeDutyLoader dutyLoader =
      new PayloadTimelinessCommitteeDutyLoader(
          validatorApiChannel, __ -> slotBasedScheduledDuties, validators, validatorIndexProvider);

  @BeforeEach
  void setUp() {
    when(validatorIndexProvider.getValidatorIndices())
        .thenReturn(SafeFuture.completedFuture(validatorIndices));
  }

  @Test
  void shouldLoadPayloadTimelinessCommitteeDuties() {
    final UInt64 epoch = UInt64.valueOf(1);
    final Bytes32 dependentRoot = dataStructureUtil.randomBytes32();

    when(validatorApiChannel.getPayloadTimelinessCommitteeDuties(epoch, validatorIndices))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    new PayloadTimelinessCommitteeDuties(
                        false,
                        dependentRoot,
                        List.of(
                            new PtcDuty(
                                validator1.getPublicKey(),
                                UInt64.valueOf(validator1Index),
                                UInt64.valueOf(9)),
                            new PtcDuty(
                                validator2.getPublicKey(),
                                UInt64.valueOf(validator2Index),
                                UInt64.valueOf(10)))))));

    loadDuties(epoch);

    verify(slotBasedScheduledDuties)
        .scheduleProduction(eq(UInt64.valueOf(9)), eq(validator1), any());
    verify(slotBasedScheduledDuties)
        .scheduleProduction(eq(UInt64.valueOf(10)), eq(validator2), any());
  }

  private void loadDuties(final UInt64 epoch) {
    final SafeFuture<Optional<SlotBasedScheduledDuties<?, ?>>> result =
        dutyLoader.loadDutiesForEpoch(epoch);
    assertThatSafeFuture(result).isCompletedWithNonEmptyOptional();
  }
}
