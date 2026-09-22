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

package tech.pegasys.teku.validator.client.duties.attestations;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps.CREATE_TOTAL;

import com.google.common.base.Suppliers;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.Validator.DutyType;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ProductionResult;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;

public class AttestationProductionDuty implements Duty {
  private static final Logger LOG = LogManager.getLogger();
  private final Int2ObjectMap<ScheduledCommittee> validatorsByCommitteeIndex =
      new Int2ObjectOpenHashMap<>();
  private final Spec spec;
  private final UInt64 slot;
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private final SendingStrategy<Attestation> sendingStrategy;
  private final ValidatorDutyMetrics validatorDutyMetrics;

  public AttestationProductionDuty(
      final Spec spec,
      final UInt64 slot,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final SendingStrategy<Attestation> sendingStrategy,
      final ValidatorDutyMetrics validatorDutyMetrics) {
    this.spec = spec;
    this.slot = slot;
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.sendingStrategy = sendingStrategy;
    this.validatorDutyMetrics = validatorDutyMetrics;
  }

  @Override
  public DutyType getType() {
    return DutyType.ATTESTATION_PRODUCTION;
  }

  /**
   * Adds a validator that should produce an attestation in this slot.
   *
   * @param validator the validator to produce an attestation
   * @param attestationCommitteeIndex the committee index for the validator
   * @param committeePosition the validator's position within the committee
   * @param validatorIndex the index of the validator
   * @param committeeSize the number of validators in the committee
   * @return a future which will be completed with the unsigned attestation for the committee.
   */
  public SafeFuture<Optional<AttestationData>> addValidator(
      final Validator validator,
      final int attestationCommitteeIndex,
      final int committeePosition,
      final int validatorIndex,
      final int committeeSize) {
    final ScheduledCommittee committee =
        validatorsByCommitteeIndex.computeIfAbsent(
            attestationCommitteeIndex, key -> new ScheduledCommittee());
    committee.addValidator(
        validator, attestationCommitteeIndex, committeePosition, validatorIndex, committeeSize);
    return committee.getAttestationDataFuture();
  }

  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.trace("Creating attestations at slot {}", slot);
    if (validatorsByCommitteeIndex.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NOOP);
    }
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(
            forkInfo ->
                sendingStrategy.send(
                    produceAllAttestations(slot, forkInfo, validatorsByCommitteeIndex)));
  }

  private Stream<SafeFuture<ProductionResult<Attestation>>> produceAllAttestations(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final Int2ObjectMap<ScheduledCommittee> validatorsByCommitteeIndex) {

    final SpecVersion specVersion = spec.atSlot(slot);

    final SignedAttestationProducer signedAttestationProducer =
        selectSignedAttestationProducer(specVersion);

    final AttestationDataProducer attestationDataProducer =
        selectAttestationDataProducer(specVersion);

    return validatorsByCommitteeIndex.int2ObjectEntrySet().stream()
        .flatMap(
            entry ->
                produceAttestationsForCommittee(
                    slot,
                    forkInfo,
                    entry.getIntKey(),
                    entry.getValue(),
                    attestationDataProducer,
                    signedAttestationProducer)
                    .stream());
  }

  private List<SafeFuture<ProductionResult<Attestation>>> produceAttestationsForCommittee(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final int committeeIndex,
      final ScheduledCommittee committee,
      final AttestationDataProducer attestationDataProducer,
      final SignedAttestationProducer signedAttestationProducer) {
    final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture =
        attestationDataProducer.createAttestationData(slot, committeeIndex);
    unsignedAttestationFuture.propagateTo(committee.getAttestationDataFuture());

    return committee.getValidators().stream()
        .map(
            validator ->
                signAttestationForValidatorInCommittee(
                    slot,
                    forkInfo,
                    committeeIndex,
                    validator,
                    signedAttestationProducer,
                    unsignedAttestationFuture))
        .toList();
  }

  private SignedAttestationProducer selectSignedAttestationProducer(final SpecVersion specVersion) {
    final SchemaDefinitions schemaDefinitions = specVersion.getSchemaDefinitions();

    return schemaDefinitions
        .toVersionElectra()
        .<SignedAttestationProducer>map(
            schemaDefinitionsElectra ->
                (attestationData, validator, signature) ->
                    createSignedSingleAttestation(
                        schemaDefinitionsElectra.getSingleAttestationSchema(),
                        attestationData,
                        validator,
                        signature))
        .orElseGet(
            () ->
                (attestationData, validator, signature) ->
                    createSignedAttestation(
                        schemaDefinitions.getAttestationSchema(),
                        attestationData,
                        validator,
                        signature));
  }

  private AttestationDataProducer selectAttestationDataProducer(final SpecVersion specVersion) {
    if (specVersion.getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
      // in Electra and later, the committee index in AttestationData is always 0, so we ask for a
      // single AttestationData which will be good for all committees in the slot
      final Supplier<SafeFuture<Optional<AttestationData>>> cachedCall =
          Suppliers.memoize(() -> createAttestationDataFromValidatorApiChannel(slot, 0));

      return (slot, committeeIndex) -> cachedCall.get();
    }

    return this::createAttestationDataFromValidatorApiChannel;
  }

  private SafeFuture<Optional<AttestationData>> createAttestationDataFromValidatorApiChannel(
      final UInt64 slot, final int committeeIndex) {
    return validatorDutyMetrics.record(
        () -> validatorApiChannel.createAttestationData(slot, committeeIndex), this, CREATE_TOTAL);
  }

  private SafeFuture<ProductionResult<Attestation>> signAttestationForValidatorInCommittee(
      final UInt64 slot,
      final ForkInfo forkInfo,
      final int committeeIndex,
      final ValidatorWithAttestationDutyInfo validator,
      final SignedAttestationProducer signedAttestationProducer,
      final SafeFuture<Optional<AttestationData>> attestationDataFuture) {
    return attestationDataFuture
        .thenCompose(
            maybeUnsignedAttestation ->
                maybeUnsignedAttestation
                    .map(
                        attestationData -> {
                          validateAttestationData(slot, attestationData);
                          return validatorDutyMetrics.record(
                              () ->
                                  signAttestationForValidator(
                                      signedAttestationProducer,
                                      forkInfo,
                                      attestationData,
                                      validator),
                              this,
                              ValidatorDutyMetricsSteps.SIGN);
                        })
                    .orElseGet(
                        () ->
                            SafeFuture.completedFuture(
                                ProductionResult.failure(
                                    validator.publicKey(),
                                    new IllegalStateException(
                                        "Unable to produce attestation for slot "
                                            + slot
                                            + " with committee "
                                            + committeeIndex
                                            + " because chain data was unavailable")))))
        .exceptionally(error -> ProductionResult.failure(validator.publicKey(), error));
  }

  private void validateAttestationData(final UInt64 slot, final AttestationData attestationData) {
    checkArgument(
        attestationData.getSlot().equals(slot),
        "Unsigned attestation slot (%s) does not match expected slot %s",
        attestationData.getSlot(),
        slot);

    // UInt64.MAX_VALUE can never be a legitimate epoch. Reject it here so a malicious or broken
    // beacon node can't get it signed and persisted as a slashing-protection floor, where it is
    // indistinguishable from "never signed" on the next read - see
    // https://github.com/Consensys-Incorporated/teku-internal/issues/334
    checkArgument(
        !attestationData.getSource().getEpoch().equals(UInt64.MAX_VALUE),
        "Unsigned attestation source epoch must not be UInt64.MAX_VALUE");
    checkArgument(
        !attestationData.getTarget().getEpoch().equals(UInt64.MAX_VALUE),
        "Unsigned attestation target epoch must not be UInt64.MAX_VALUE");

    spec.atSlot(slot)
        .getAttestationUtil()
        .validateCommitteeIndexValue(attestationData.getIndex())
        .getReason()
        .ifPresent(
            reason -> {
              throw new IllegalArgumentException(reason);
            });
  }

  private SafeFuture<ProductionResult<Attestation>> signAttestationForValidator(
      final SignedAttestationProducer signedAttestationProducer,
      final ForkInfo forkInfo,
      final AttestationData attestationData,
      final ValidatorWithAttestationDutyInfo validator) {
    return validator
        .signer()
        .signAttestationData(attestationData, forkInfo)
        .thenApply(
            signature ->
                signedAttestationProducer.createSignedAttestation(
                    attestationData, validator, signature))
        .thenApply(
            attestation ->
                ProductionResult.success(
                    validator.publicKey(), attestationData.getBeaconBlockRoot(), attestation));
  }

  private Attestation createSignedAttestation(
      final AttestationSchema<?> attestationSchema,
      final AttestationData attestationData,
      final ValidatorWithAttestationDutyInfo validator,
      final BLSSignature signature) {
    final SszBitlist aggregationBits =
        attestationSchema
            .getAggregationBitsSchema()
            .ofBits(validator.committeeSize(), validator.committeePosition());

    final Supplier<SszBitvector> committeeBitsSupplier =
        attestationSchema
            .getCommitteeBitsSchema()
            .<Supplier<SszBitvector>>map(
                committeeBitsSchema -> () -> committeeBitsSchema.ofBits(validator.committeeIndex()))
            .orElse(() -> null);
    return attestationSchema.create(
        aggregationBits, attestationData, signature, committeeBitsSupplier);
  }

  private Attestation createSignedSingleAttestation(
      final SingleAttestationSchema attestationSchema,
      final AttestationData attestationData,
      final ValidatorWithAttestationDutyInfo validator,
      final BLSSignature signature) {
    return attestationSchema.create(
        UInt64.valueOf(validator.committeeIndex()),
        UInt64.valueOf(validator.validatorIndex()),
        attestationData,
        signature);
  }

  @FunctionalInterface
  private interface SignedAttestationProducer {
    Attestation createSignedAttestation(
        final AttestationData attestationData,
        final ValidatorWithAttestationDutyInfo validator,
        final BLSSignature signature);
  }

  @FunctionalInterface
  private interface AttestationDataProducer {
    SafeFuture<Optional<AttestationData>> createAttestationData(
        final UInt64 slot, int committeeIndex);
  }
}
