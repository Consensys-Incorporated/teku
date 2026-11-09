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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
public class BeaconProposerPreparerTest {
  private final int validator1Index = 19;
  private final int validator2Index = 23;
  private final int validator3Index = 24;
  private final ValidatorIndexProvider validatorIndexProvider = mock(ValidatorIndexProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider =
      mock(ProposerConfigPropertiesProvider.class);

  private BeaconProposerPreparer beaconProposerPreparer;
  private Eth1Address defaultFeeRecipient;
  private Eth1Address validator1FeeRecipientConfig;

  private long slotsPerEpoch;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    final Spec spec = specContext.getSpec();

    final Validator validator1 =
        new Validator(
            specContext.getDataStructureUtil().randomPublicKey(),
            mock(Signer.class),
            Optional::empty);
    final Validator validator2 =
        new Validator(
            specContext.getDataStructureUtil().randomPublicKey(),
            mock(Signer.class),
            Optional::empty);
    final Validator validator3 =
        new Validator(
            specContext.getDataStructureUtil().randomPublicKey(),
            mock(Signer.class),
            Optional::empty);

    final UInt64 validator1GasLimitConfig = specContext.getDataStructureUtil().randomUInt64();

    final Map<BLSPublicKey, Integer> validatorIndicesByPublicKey =
        Map.of(
            validator1.getPublicKey(), validator1Index,
            validator2.getPublicKey(), validator2Index,
            validator3.getPublicKey(), validator3Index);

    defaultFeeRecipient = specContext.getDataStructureUtil().randomEth1Address();
    validator1FeeRecipientConfig = specContext.getDataStructureUtil().randomEth1Address();

    beaconProposerPreparer =
        new BeaconProposerPreparer(
            validatorApiChannel,
            Optional.of(validatorIndexProvider),
            proposerConfigPropertiesProvider,
            spec);
    beaconProposerPreparer.initialize(Optional.of(validatorIndexProvider));

    slotsPerEpoch = spec.getSlotsPerEpoch(UInt64.ZERO);

    when(validatorIndexProvider.getValidatorIndicesByPublicKey())
        .thenReturn(SafeFuture.completedFuture(validatorIndicesByPublicKey));

    when(proposerConfigPropertiesProvider.getFeeRecipient(validator1.getPublicKey()))
        .thenReturn(Optional.of(validator1FeeRecipientConfig));
    when(proposerConfigPropertiesProvider.getGasLimit(validator1.getPublicKey()))
        .thenReturn(validator1GasLimitConfig);

    when(proposerConfigPropertiesProvider.getFeeRecipient(validator2.getPublicKey()))
        .thenReturn(Optional.of(defaultFeeRecipient));
    when(proposerConfigPropertiesProvider.getFeeRecipient(validator3.getPublicKey()))
        .thenReturn(Optional.of(defaultFeeRecipient));

    when(proposerConfigPropertiesProvider.refresh()).thenReturn(SafeFuture.COMPLETE);

    when(validatorApiChannel.prepareBeaconProposer(anyList())).thenReturn(SafeFuture.COMPLETE);
  }

  @TestTemplate
  void send_shouldIncludeRuntimeFeeRecipients() throws SetFeeRecipientException {
    // validator 3 is configured from API
    ArgumentCaptor<Collection<BeaconPreparableProposer>> captor = doCall();

    assertThat(captor.getValue())
        .containsExactlyInAnyOrder(
            new BeaconPreparableProposer(
                UInt64.valueOf(validator1Index), validator1FeeRecipientConfig),
            new BeaconPreparableProposer(UInt64.valueOf(validator2Index), defaultFeeRecipient),
            new BeaconPreparableProposer(UInt64.valueOf(validator3Index), defaultFeeRecipient));
  }

  @TestTemplate
  void should_notCallPrepareBeaconProposerUnlessFirstTimeOrThirdSlotOfEpoch() {
    beaconProposerPreparer.onSlot(UInt64.ZERO);
    beaconProposerPreparer.onSlot(UInt64.valueOf(2));
    verify(validatorApiChannel, times(2)).prepareBeaconProposer(any());

    beaconProposerPreparer.onSlot(UInt64.ONE);
    beaconProposerPreparer.onSlot(UInt64.valueOf(3));
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @TestTemplate
  void should_callPrepareBeaconProposerAfterOnPossibleMissedEvents() {
    beaconProposerPreparer.onPossibleMissedEvents();
    verify(validatorApiChannel).prepareBeaconProposer(any());
  }

  @TestTemplate
  void should_callPrepareBeaconProposerAfterOnValidatorsAdded() {
    beaconProposerPreparer.onValidatorsAdded();
    verify(validatorApiChannel).prepareBeaconProposer(any());
  }

  @TestTemplate
  void should_catchApiExceptions() {
    when(validatorApiChannel.prepareBeaconProposer(anyList()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("error")));

    beaconProposerPreparer.onSlot(UInt64.ZERO);
    verify(validatorApiChannel, times(1)).prepareBeaconProposer(any());
  }

  private ArgumentCaptor<Collection<BeaconPreparableProposer>> doCall() {
    beaconProposerPreparer.onSlot(UInt64.valueOf(slotsPerEpoch * 2).plus(2));

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Collection<BeaconPreparableProposer>> captor =
        ArgumentCaptor.forClass(Collection.class);
    verify(validatorApiChannel).prepareBeaconProposer(captor.capture());

    return captor;
  }
}
