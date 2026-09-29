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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

/**
 * Verifies that {@link ProposerPreferencesPublisher} does nothing related to proposer preferences
 * pre Gloas. Fork-awareness lives in {@link
 * tech.pegasys.teku.spec.logic.common.util.ProposerPreferencesUtil} which returns NOOP before Gloas
 */
class ProposerPreferencesPublisherPhase0Test {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider =
      mock(ProposerConfigPropertiesProvider.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final Signer signer = mock(Signer.class);

  private final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
  private final Eth1Address feeRecipient = dataStructureUtil.randomEth1Address();
  private final UInt64 gasLimit = dataStructureUtil.randomUInt64();
  private final Validator validator = new Validator(publicKey, signer, Optional::empty);
  private final OwnedValidators ownedValidators = new OwnedValidators(Map.of(publicKey, validator));

  private final ProposerPreferencesPublisher publisher =
      new ProposerPreferencesPublisher(
          ownedValidators,
          spec,
          validatorApiChannel,
          proposerConfigPropertiesProvider,
          forkProvider);

  @BeforeEach
  void setUp() {
    when(proposerConfigPropertiesProvider.getFeeRecipient(publicKey))
        .thenReturn(Optional.of(feeRecipient));
    when(proposerConfigPropertiesProvider.getGasLimit(publicKey)).thenReturn(gasLimit);
    when(forkProvider.getForkInfo(any()))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomForkInfo()));
  }

  @Test
  void shouldNotSignOrPublishPreGloas() {
    final UInt64 epoch = UInt64.valueOf(6);
    final UInt64 slot = spec.computeStartSlotAtEpoch(epoch);

    publisher.onProposerDutiesLoaded(
        epoch,
        new ProposerDuties(
            dataStructureUtil.randomBytes32(),
            List.of(new ProposerDuty(publicKey, 42, slot)),
            false));

    verify(signer, never()).signProposerPreferences(any(), any());
    verify(validatorApiChannel, never()).sendSignedProposerPreferences(anyList());
  }
}
