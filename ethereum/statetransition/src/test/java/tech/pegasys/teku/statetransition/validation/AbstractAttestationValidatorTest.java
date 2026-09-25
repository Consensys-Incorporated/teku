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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

/**
 * The following validations MUST pass before forwarding the attestation on the subnet.
 *
 * <p>The attestation's committee index (attestation.data.index) is for the correct subnet.
 *
 * <ul>
 *   <li>Phase 0
 *       <p>attestation.data.slot is within the last ATTESTATION_PROPAGATION_SLOT_RANGE slots
 *       (within a * MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. attestation.data.slot + *
 *       ATTESTATION_PROPAGATION_SLOT_RANGE >= current_slot >= attestation.data.slot (a client MAY
 *       queue * future attestations for processing at the appropriate slot).
 *   <li>Deneb
 *       <p>attestation.data.slot is equal to or earlier than the current_slot (with a
 *       MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. attestation.data.slot <= current_slot (a
 *       client MAY queue future attestation for processing at the appropriate slot).
 *       <p>the epoch of attestation.data.slot is either the current or previous epoch (with a
 *       MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e.
 *       compute_epoch_at_slot(attestation.data.slot) in (get_previous_epoch(state),
 *       get_current_epoch(state))
 * </ul>
 *
 * <p>The attestation is unaggregated -- that is, it has exactly one participating validator
 * (len([bit for bit in attestation.aggregation_bits if bit == 0b1]) == 1).
 *
 * <p>The attestation is the first valid attestation received for the participating validator for
 * the slot, attestation.data.slot.
 *
 * <p>The block being voted for (attestation.data.beacon_block_root) passes validation.
 *
 * <p>The signature of attestation is valid.
 */
abstract class AbstractAttestationValidatorTest {
  private static final Logger LOG = LogManager.getLogger();

  protected final Spec spec =
      createSpec(builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
  protected final AttestationSchema<?> attestationSchema =
      spec.getGenesisSchemaDefinitions().getAttestationSchema();
  protected final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.create()
          .numberOfValidators(64)
          .specProvider(spec)
          .storageMode(StateStorageMode.ARCHIVE)
          .build();
  protected final RecentChainData recentChainData = storageSystem.recentChainData();
  protected final ChainBuilder chainBuilder = storageSystem.chainBuilder();
  protected final ChainUpdater chainUpdater = storageSystem.chainUpdater();
  protected final AttestationGenerator attestationGenerator =
      new AttestationGenerator(spec, chainBuilder.getValidatorKeys());
  protected final AsyncBLSSignatureVerifier signatureVerifier =
      AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE);
  protected final GossipValidationHelper gossipValidationHelper =
      new GossipValidationHelper(spec, recentChainData, new StubMetricsSystem());
  protected final Map<Bytes32, BlockImportResult> invalidBlockRoots = new ConcurrentHashMap<>();
  protected final AttestationValidator validator =
      new AttestationValidator(spec, signatureVerifier, gossipValidationHelper, invalidBlockRoots);

  @BeforeEach
  public void setUp() {
    storageSystem.chainUpdater().initializeGenesis(false);
  }

  public abstract Spec createSpec(final Consumer<SpecConfigBuilder> configAdapter);

  /**
   * [IGNORE] There has been no other valid attestation seen on an attestation subnet that has an
   * identical attestation.data.target.epoch and participating validator index.
   */
  @Test
  public void shouldIgnoreSecondAttestationFromSameValidatorForSameTargetEpoch() {
    // Two blocks within the same epoch so the same committee can vote for two different heads
    final StateAndBlockSummary block1 = chainUpdater.advanceChain(UInt64.ONE);
    final StateAndBlockSummary block2 = chainUpdater.advanceChain(UInt64.valueOf(2));
    final UInt64 attestationSlot = UInt64.valueOf(3);
    chainUpdater.setCurrentSlot(attestationSlot);

    // Attester from the slot 3 committee voting for the head (block2)
    final Attestation attestation1 =
        attestationGenerator.streamAttestations(block2, attestationSlot).findFirst().orElseThrow();
    // The same attester voting for block1 instead: same target epoch, different attestation data
    final Attestation attestation2 =
        attestationGenerator
            .streamAttestations(block1, attestationSlot)
            .filter(attestation -> hasSameValidators(attestation1, attestation))
            .findFirst()
            .orElseThrow();

    // Sanity checks
    assertThat(attestation1.getData().getTarget().getEpoch())
        .isEqualTo(attestation2.getData().getTarget().getEpoch());
    assertThat(attestation1.getData().getBeaconBlockRoot())
        .isNotEqualTo(attestation2.getData().getBeaconBlockRoot());

    assertThat(validate(attestation1)).isEqualTo(InternalValidationResult.ACCEPT);
    assertThat(validate(attestation2).code()).isEqualTo(IGNORE);
  }

  protected Predicate<? super CompletableFuture<InternalValidationResult>> rejected(
      final String messageContents) {
    return result -> {
      try {
        InternalValidationResult internalValidationResult = result.get();
        return internalValidationResult.isReject()
            && internalValidationResult.getDescription().orElseThrow().contains(messageContents);
      } catch (Exception e) {
        LOG.error("failed to evaluate rejected predicate", e);
        return false;
      }
    };
  }

  protected InternalValidationResult validate(final Attestation attestation) {
    final BeaconState state = safeJoin(recentChainData.getBestState().orElseThrow());

    return validator
        .validate(
            ValidatableAttestation.fromNetwork(
                spec, attestation, spec.computeSubnetForAttestation(state, attestation)))
        .join();
  }

  protected boolean hasSameValidators(
      final Attestation attestation1, final Attestation attestation) {
    return attestation.getAggregationBits().equals(attestation1.getAggregationBits());
  }
}
