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

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.metrics.Validator.DutyType;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AggregateAndProofPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationAndProofPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.PqAttestationSchemas;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SignedAggregateAndProofPq;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.signatures.xmss.XmssSigner;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ProductionResult;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDutyAggregators.CommitteeAggregator;

public class AggregationDuty implements Duty {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final UInt64 slot;
  private final ValidatorApiChannel validatorApiChannel;
  private final AggregationDutyAggregators aggregators;
  private final ForkProvider forkProvider;
  private final ValidatorLogger validatorLogger;
  private final SendingStrategy<SignedAggregateAndProof> sendingStrategy;
  private final ValidatorDutyMetrics validatorDutyMetrics;
  private final Optional<AsyncRunner> asyncRunner;

  public AggregationDuty(
      final Spec spec,
      final UInt64 slot,
      final ValidatorApiChannel validatorApiChannel,
      final AggregationDutyAggregators aggregators,
      final ForkProvider forkProvider,
      final ValidatorLogger validatorLogger,
      final SendingStrategy<SignedAggregateAndProof> sendingStrategy,
      final ValidatorDutyMetrics validatorDutyMetrics) {
    this(
        spec,
        slot,
        validatorApiChannel,
        aggregators,
        forkProvider,
        validatorLogger,
        sendingStrategy,
        validatorDutyMetrics,
        Optional.empty());
  }

  /**
   * @param asyncRunner delays each PQ aggregator by its position in the aggregation committee;
   *     without it, every aggregator aggregates when the duty runs
   */
  public AggregationDuty(
      final Spec spec,
      final UInt64 slot,
      final ValidatorApiChannel validatorApiChannel,
      final AggregationDutyAggregators aggregators,
      final ForkProvider forkProvider,
      final ValidatorLogger validatorLogger,
      final SendingStrategy<SignedAggregateAndProof> sendingStrategy,
      final ValidatorDutyMetrics validatorDutyMetrics,
      final Optional<AsyncRunner> asyncRunner) {
    this.spec = spec;
    this.slot = slot;
    this.validatorApiChannel = validatorApiChannel;
    this.aggregators = aggregators;
    this.forkProvider = forkProvider;
    this.validatorLogger = validatorLogger;
    this.sendingStrategy = sendingStrategy;
    this.validatorDutyMetrics = validatorDutyMetrics;
    this.asyncRunner = asyncRunner;
  }

  @Override
  public DutyType getType() {
    return DutyType.ATTESTATION_AGGREGATION;
  }

  /**
   * Adds a validator to this duty. Only one aggregate per committee will be produced even if
   * multiple validators are added for that committee. The aggregated attestations would be
   * identical anyway so sending all of them would be a waste of network bandwidth.
   *
   * @param validatorIndex the validator's index
   * @param proof the validator's slot signature proving it is the aggregator
   * @param attestationCommitteeIndex the committee index to aggregate
   * @param unsignedAttestationFuture the future returned by {@link
   *     AttestationProductionDuty#addValidator(Validator, int, int, int, int)} which completes with
   *     the unsigned attestation for this committee and slot.
   */
  public void addValidator(
      final Validator validator,
      final int validatorIndex,
      final BLSSignature proof,
      final int attestationCommitteeIndex,
      final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture) {
    addValidator(
        validator, validatorIndex, proof, attestationCommitteeIndex, 0, unsignedAttestationFuture);
  }

  /**
   * Adds a validator to this duty, at {@code aggregatorPosition} in its aggregation committee from
   * PQ, which delays its aggregate by that many AGGREGATOR_DELAY_BPS.
   */
  public void addValidator(
      final Validator validator,
      final int validatorIndex,
      final BLSSignature proof,
      final int attestationCommitteeIndex,
      final int aggregatorPosition,
      final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture) {
    aggregators.addValidator(
        validator,
        validatorIndex,
        proof,
        attestationCommitteeIndex,
        aggregatorPosition,
        unsignedAttestationFuture);
  }

  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.trace("Aggregating attestations at slot {}", slot);
    if (!aggregators.hasAggregators()) {
      return SafeFuture.completedFuture(DutyResult.NOOP);
    }
    if (spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.PQ)) {
      // PQ: aggregators come from the aggregation committee, and the beacon node proves the
      // aggregate's signatures. Each is sent once signed, as leanXMSS signing is slow.
      return new IndividualSendingStrategy<SignedAggregateAndProofPq>(
              validatorApiChannel::sendPqAggregateAndProofs)
          .send(
              aggregators
                  .streamAggregators()
                  .map(
                      aggregator ->
                          afterAggregatorDelay(aggregator)
                              .thenCompose(__ -> aggregatePqCommittee(aggregator))));
    }
    return sendingStrategy.send(aggregators.streamAggregators().map(this::aggregateCommittee));
  }

  /**
   * The duty runs when the first aggregator's aggregate is due (get_aggregate_due_ms(0)); the
   * aggregator at position p is due p AGGREGATOR_DELAY_BPS later.
   */
  private SafeFuture<Void> afterAggregatorDelay(final CommitteeAggregator aggregator) {
    if (aggregator.aggregatorPosition() == 0 || asyncRunner.isEmpty()) {
      return SafeFuture.COMPLETE;
    }
    final ForkChoiceUtil forkChoiceUtil = spec.atSlot(slot).getForkChoiceUtil();
    final int delayMillis =
        forkChoiceUtil.getAggregateDueMillis(aggregator.aggregatorPosition())
            - forkChoiceUtil.getAggregateDueMillis(0);
    return asyncRunner.get().getDelayedFuture(Duration.ofMillis(delayMillis));
  }

  private SafeFuture<ProductionResult<SignedAggregateAndProofPq>> aggregatePqCommittee(
      final CommitteeAggregator aggregator) {
    final Optional<XmssSigner> maybeXmssSigner = aggregator.validator().getXmssSigner();
    if (maybeXmssSigner.isEmpty()) {
      return SafeFuture.completedFuture(
          ProductionResult.failure(
              aggregator.validator().getPublicKey(),
              new IllegalStateException("Validator has no PQ keys to aggregate with")));
    }
    return aggregator
        .unsignedAttestationFuture()
        .thenCompose(
            maybeAttestation -> {
              if (maybeAttestation.isEmpty()) {
                return SafeFuture.completedFuture(
                    ProductionResult.<SignedAggregateAndProofPq>failure(
                        aggregator.validator().getPublicKey(),
                        new IllegalStateException(
                            "Unable to perform aggregation for committee because no attestation was produced")));
              }
              return validatorDutyMetrics
                  .record(
                      () ->
                          validatorApiChannel.createPqAggregate(
                              slot,
                              maybeAttestation.get().hashTreeRoot(),
                              aggregator.attestationCommitteeIndex()),
                      this,
                      ValidatorDutyMetricsSteps.CREATE)
                  .thenCompose(
                      maybeAggregate -> {
                        if (maybeAggregate.isEmpty()) {
                          // Usually another aggregator's aggregate already includes all of this
                          // one's attesters (should_broadcast_aggregate), which staggered
                          // aggregators expect, so it's no warning; the beacon node logs why
                          LOG.debug(
                              "No PQ aggregate to publish for committee {} at slot {}",
                              aggregator.attestationCommitteeIndex(),
                              slot);
                          return SafeFuture.completedFuture(
                              ProductionResult.<SignedAggregateAndProofPq>noop(
                                  aggregator.validator().getPublicKey()));
                        }
                        return validatorDutyMetrics.record(
                            () ->
                                signPqAggregate(
                                    aggregator, maybeXmssSigner.get(), maybeAggregate.get()),
                            this,
                            ValidatorDutyMetricsSteps.SIGN);
                      });
            })
        .exceptionally(
            error -> ProductionResult.failure(aggregator.validator().getPublicKey(), error));
  }

  /** get_aggregate_and_proof and its XMSS signature at the aggregate leaf. */
  private SafeFuture<ProductionResult<SignedAggregateAndProofPq>> signPqAggregate(
      final CommitteeAggregator aggregator,
      final XmssSigner xmssSigner,
      final AttestationAndProofPq aggregateWithProof) {
    final PqAttestationSchemas schemas =
        SchemaDefinitionsPq.required(spec.atSlot(slot).getSchemaDefinitions())
            .getPqAttestationSchemas();
    final AggregateAndProofPq aggregateAndProof =
        schemas
            .getAggregateAndProofSchema()
            .create(
                aggregator.validatorIndex(),
                aggregateWithProof.getAggregate(),
                aggregateWithProof.getProof());
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(forkInfo -> xmssSigner.signAggregateAndProof(aggregateAndProof, forkInfo))
        .thenApply(
            signature ->
                ProductionResult.success(
                    aggregator.validator().getPublicKey(),
                    aggregateAndProof.getAggregate().getData().getBeaconBlockRoot(),
                    schemas
                        .getSignedAggregateAndProofSchema()
                        .create(aggregateAndProof, signature.toBytes())));
  }

  private SafeFuture<ProductionResult<SignedAggregateAndProof>> aggregateCommittee(
      final CommitteeAggregator aggregator) {
    return aggregator
        .unsignedAttestationFuture()
        .thenCompose(maybeAttestation -> createAggregate(aggregator, maybeAttestation))
        .exceptionally(
            error -> ProductionResult.failure(aggregator.validator().getPublicKey(), error));
  }

  private SafeFuture<ProductionResult<SignedAggregateAndProof>> createAggregate(
      final CommitteeAggregator aggregator, final Optional<AttestationData> maybeAttestation) {
    if (maybeAttestation.isEmpty()) {
      return SafeFuture.completedFuture(
          ProductionResult.failure(
              aggregator.validator().getPublicKey(),
              new IllegalStateException(
                  "Unable to perform aggregation for committee because no attestation was produced")));
    }

    final AttestationData attestationData = maybeAttestation.get();

    final SafeFuture<Optional<Attestation>> createAggregationFuture =
        validatorDutyMetrics.record(
            () ->
                validatorApiChannel.createAggregate(
                    slot,
                    attestationData.hashTreeRoot(),
                    Optional.of(aggregator.attestationCommitteeIndex())),
            this,
            ValidatorDutyMetricsSteps.CREATE);

    return createAggregationFuture.thenCompose(
        maybeAggregate -> {
          if (maybeAggregate.isEmpty()) {
            validatorLogger.aggregationSkipped(slot, aggregator.attestationCommitteeIndex());
            return SafeFuture.completedFuture(
                ProductionResult.noop(aggregator.validator().getPublicKey()));
          }
          final Attestation aggregate = maybeAggregate.get();
          return validatorDutyMetrics.record(
              () -> createSignedAggregateAndProof(aggregator, aggregate),
              this,
              ValidatorDutyMetricsSteps.SIGN);
        });
  }

  private SafeFuture<ProductionResult<SignedAggregateAndProof>> createSignedAggregateAndProof(
      final CommitteeAggregator aggregator, final Attestation aggregate) {
    final SchemaDefinitions schemaDefinitions =
        spec.atSlot(aggregate.getData().getSlot()).getSchemaDefinitions();
    final AggregateAndProof aggregateAndProof =
        schemaDefinitions
            .getAggregateAndProofSchema()
            .create(aggregator.validatorIndex(), aggregate, aggregator.proof());
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(
            forkInfo ->
                aggregator
                    .validator()
                    .getSigner()
                    .signAggregateAndProof(aggregateAndProof, forkInfo)
                    .thenApply(
                        signature ->
                            ProductionResult.success(
                                aggregator.validator().getPublicKey(),
                                aggregateAndProof.getAggregate().getData().getBeaconBlockRoot(),
                                schemaDefinitions
                                    .getSignedAggregateAndProofSchema()
                                    .create(aggregateAndProof, signature))));
  }

  @VisibleForTesting
  AggregationDutyAggregators getAggregators() {
    return aggregators;
  }
}
