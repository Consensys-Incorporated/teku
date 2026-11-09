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

package tech.pegasys.teku.spec.generator;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.signatures.LocalSigner;
import tech.pegasys.teku.spec.signatures.Signer;

public class AggregateGenerator {
  private final Spec spec;
  private final AttestationGenerator attestationGenerator;
  private final List<BLSKeyPair> validatorKeys;

  public AggregateGenerator(final Spec spec, final List<BLSKeyPair> validatorKeys) {
    this.spec = spec;
    attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    this.validatorKeys = validatorKeys;
  }

  public AttestationGenerator getAttestationGenerator() {
    return attestationGenerator;
  }

  public Generator generator() {
    return new Generator();
  }

  public SignedAggregateAndProof validAggregateAndProof(final StateAndBlockSummary blockAndState) {
    return generator().blockAndState(blockAndState, blockAndState.getSlot()).generate();
  }

  public SignedAggregateAndProof validAggregateAndProof(
      final StateAndBlockSummary blockAndState, final UInt64 slot) {
    return generator().blockAndState(blockAndState, slot).generate();
  }

  private Signer getSignerForValidatorIndex(final int validatorIndex) {
    return new LocalSigner(spec, validatorKeys.get(validatorIndex), SyncAsyncRunner.SYNC_RUNNER);
  }

  public class Generator {
    // The latest block being attested to
    private StateAndBlockSummary blockAndState;
    // The latest state processed through to the current slot
    private BeaconState stateAtSlot;
    private UInt64 slot;
    private Optional<UInt64> aggregatorIndex = Optional.empty();
    private Optional<Attestation> aggregate = Optional.empty();
    private Optional<BLSSignature> selectionProof = Optional.empty();
    private Optional<UInt64> committeeIndex = Optional.empty();

    public Generator blockAndState(final StateAndBlockSummary blockAndState) {
      return blockAndState(blockAndState, blockAndState.getSlot());
    }

    public Generator blockAndState(final StateAndBlockSummary blockAndState, final UInt64 slot) {
      this.slot = slot;
      this.blockAndState = blockAndState;
      this.stateAtSlot = generateHeadState(blockAndState.getState(), slot);
      return this;
    }

    public Generator aggregatorIndex(final UInt64 aggregatorIndex) {
      this.aggregatorIndex = Optional.of(aggregatorIndex);
      return this;
    }

    public Generator aggregate(final Attestation aggregate) {
      this.aggregate = Optional.of(aggregate);
      return this;
    }

    public Generator committeeIndex(final UInt64 committeeIndex) {
      this.committeeIndex = Optional.of(committeeIndex);
      return this;
    }

    public Generator selectionProof(final BLSSignature selectionProof) {
      this.selectionProof = Optional.of(selectionProof);
      return this;
    }

    public SignedAggregateAndProof generate() {
      checkNotNull(blockAndState, "Missing block");
      checkNotNull(slot, "Missing slot");
      final Attestation aggregate = this.aggregate.orElseGet(() -> createAttestation(slot));

      return this.aggregatorIndex
          .map(index -> generateWithFixedAggregatorIndex(slot, aggregate, index))
          .orElseGet(() -> generateWithAnyValidAggregatorIndex(aggregate));
    }

    private SignedAggregateAndProof generateWithAnyValidAggregatorIndex(
        final Attestation aggregate) {
      final List<Integer> beaconCommittee =
          spec.getBeaconCommittee(
              stateAtSlot, aggregate.getData().getSlot(), aggregate.getFirstCommitteeIndex());
      for (int validatorIndex : beaconCommittee) {
        final Optional<BLSSignature> maybeSelectionProof =
            createValidSelectionProof(validatorIndex, stateAtSlot, aggregate);
        if (maybeSelectionProof.isPresent()) {
          return generate(aggregate, UInt64.valueOf(validatorIndex), maybeSelectionProof.get());
        }
      }

      throw new NoSuchElementException("No valid aggregate possible");
    }

    private SignedAggregateAndProof generateWithFixedAggregatorIndex(
        final UInt64 slot, final Attestation aggregate, final UInt64 aggregatorIndex) {
      final BLSSignature validSelectionProof =
          createSelectionProof(aggregatorIndex.intValue(), stateAtSlot, slot);
      return generate(aggregate, aggregatorIndex, validSelectionProof);
    }

    private SignedAggregateAndProof generate(
        final Attestation aggregate,
        final UInt64 aggregatorIndex,
        final BLSSignature validSelectionProof) {
      final BLSSignature selectionProof = this.selectionProof.orElse(validSelectionProof);
      final AggregateAndProof aggregateAndProof =
          spec.atSlot(aggregate.getData().getSlot())
              .getSchemaDefinitions()
              .getAggregateAndProofSchema()
              .create(aggregatorIndex, aggregate, selectionProof);
      return createSignedAggregateAndProof(aggregatorIndex, aggregateAndProof, stateAtSlot);
    }

    private Attestation createAttestation(final UInt64 slot) {
      return committeeIndex
          .map(committeeIndex -> createAttestationForCommittee(slot, committeeIndex))
          .orElseGet(() -> attestationGenerator.validAttestation(blockAndState, slot));
    }

    private Attestation createAttestationForCommittee(
        final UInt64 slot, final UInt64 committeeIndex) {
      return attestationGenerator
          .streamAttestations(blockAndState, slot)
          .filter(attestation -> attestation.getFirstCommitteeIndex().equals(committeeIndex))
          .findFirst()
          .orElseThrow();
    }

    private Optional<BLSSignature> createValidSelectionProof(
        final int validatorIndex, final BeaconState state, final Attestation attestation) {
      final UInt64 slot = attestation.getData().getSlot();
      final UInt64 committeeIndex = attestation.getFirstCommitteeIndex();
      final SpecVersion specVersion = spec.atSlot(slot);
      final List<Integer> beaconCommittee = spec.getBeaconCommittee(state, slot, committeeIndex);
      final int aggregatorModulo =
          specVersion.getValidatorsUtil().getAggregatorModulo(beaconCommittee.size());
      final BLSSignature selectionProof = createSelectionProof(validatorIndex, state, slot);
      if (specVersion.getValidatorsUtil().isAggregator(selectionProof, aggregatorModulo)) {
        return Optional.of(selectionProof);
      }
      return Optional.empty();
    }

    private BLSSignature createSelectionProof(
        final int validatorIndex, final BeaconState state, final UInt64 slot) {
      return getSignerForValidatorIndex(validatorIndex)
          .signAggregationSlot(slot, state.getForkInfo())
          .join();
    }

    private SignedAggregateAndProof createSignedAggregateAndProof(
        final UInt64 aggregatorIndex,
        final AggregateAndProof aggregateAndProof,
        final BeaconState state) {
      final BLSSignature aggregateSignature =
          getSignerForValidatorIndex(aggregatorIndex.intValue())
              .signAggregateAndProof(aggregateAndProof, state.getForkInfo())
              .join();
      return spec.atSlot(aggregateAndProof.getAggregate().getData().getSlot())
          .getSchemaDefinitions()
          .getSignedAggregateAndProofSchema()
          .create(aggregateAndProof, aggregateSignature);
    }

    private BeaconState generateHeadState(final BeaconState state, final UInt64 slot) {
      if (state.getSlot().equals(slot)) {
        return state;
      }

      try {
        return spec.processSlots(state, slot);
      } catch (EpochProcessingException | SlotProcessingException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
