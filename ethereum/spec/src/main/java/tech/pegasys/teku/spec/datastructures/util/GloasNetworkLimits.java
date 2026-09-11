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

package tech.pegasys.teku.spec.datastructures.util;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.schema.SszNetworkValidator;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsGloas;

/**
 * Count limits that Gloas no longer expresses in SSZ (EIP-7688 progressive lists) and that the spec
 * allows to be enforced when deserializing network messages. Shared by the network decoders
 * (through the wire-level schema validators) and by the gossip validators, which also see locally
 * published messages that never crossed the wire.
 */
public final class GloasNetworkLimits {

  /** Subject used in violation messages for a block's {@code parent_execution_requests}. */
  public static final String PARENT_EXECUTION_REQUESTS_SUBJECT = "Parent execution requests";

  /** Subject used in violation messages for an execution payload envelope. */
  public static final String EXECUTION_PAYLOAD_ENVELOPE_SUBJECT = "Execution payload envelope";

  private GloasNetworkLimits() {}

  /**
   * Validator for blocks decoded from the network: spec {@code verify_block_body_operation_limits}
   * and {@code verify_execution_requests_limits} on the parent execution requests.
   */
  public static SszNetworkValidator<SignedBeaconBlock> signedBeaconBlockNetworkValidator(
      final SpecConfigGloas config) {
    return block -> {
      final BeaconBlockBodyGloas body = BeaconBlockBodyGloas.required(block.getMessage().getBody());
      verifyBlockBodyOperationLimits(body, config)
          .or(
              () ->
                  verifyExecutionRequestsLimits(
                      PARENT_EXECUTION_REQUESTS_SUBJECT,
                      ExecutionRequestsGloas.required(body.getParentExecutionRequests()),
                      config))
          .ifPresent(GloasNetworkLimits::rejectDecoded);
    };
  }

  /**
   * Validator for execution payload envelopes decoded from the network: the execution request and
   * withdrawal count limits of {@code validate_execution_payload_envelope_gossip}.
   */
  public static SszNetworkValidator<SignedExecutionPayloadEnvelope>
      signedExecutionPayloadEnvelopeNetworkValidator(final SpecConfigGloas config) {
    return signedEnvelope -> {
      final ExecutionPayloadEnvelope envelope = signedEnvelope.getMessage();
      verifyExecutionRequestsLimits(
              EXECUTION_PAYLOAD_ENVELOPE_SUBJECT,
              ExecutionRequestsGloas.required(envelope.getExecutionRequests()),
              config)
          .or(
              () ->
                  verifyWithdrawalsLimit(
                      EXECUTION_PAYLOAD_ENVELOPE_SUBJECT, envelope.getPayload(), config))
          .ifPresent(GloasNetworkLimits::rejectDecoded);
    };
  }

  private static void rejectDecoded(final LimitViolation violation) {
    throw new SszDeserializeException(violation.describe());
  }

  /** The first count that exceeds its limit. A zero limit means the list must be empty. */
  public record LimitViolation(String subject, String description, int count, int limit) {
    public String describe() {
      if (limit == 0) {
        return String.format("%s must not contain %s, found %d", subject, description, count);
      }
      return String.format("%s has %d %s, max allowed %d", subject, count, description, limit);
    }
  }

  /** Spec {@code verify_block_body_operation_limits}. */
  public static Optional<LimitViolation> verifyBlockBodyOperationLimits(
      final BeaconBlockBodyGloas body, final SpecConfigGloas config) {
    final String subject = "Block";
    return check(
            subject,
            "proposer slashings",
            body.getProposerSlashings().size(),
            config.getMaxProposerSlashings())
        .or(
            () ->
                check(
                    subject,
                    "attester slashings",
                    body.getAttesterSlashings().size(),
                    config.getMaxAttesterSlashingsElectra()))
        .or(
            () ->
                check(
                    subject,
                    "attestations",
                    body.getAttestations().size(),
                    config.getMaxAttestationsElectra()))
        .or(() -> check(subject, "deposits", body.getDeposits().size(), 0))
        .or(
            () ->
                check(
                    subject,
                    "voluntary exits",
                    body.getVoluntaryExits().size(),
                    config.getMaxVoluntaryExits()))
        .or(
            () ->
                check(
                    subject,
                    "bls to execution changes",
                    body.getBlsToExecutionChanges().size(),
                    config.getMaxBlsToExecutionChanges()))
        .or(
            () ->
                check(
                    subject,
                    "payload attestations",
                    body.getPayloadAttestations().size(),
                    config.getMaxPayloadAttestations()));
  }

  /** Spec {@code verify_execution_requests_limits}. Deposit requests have no Gloas limit. */
  public static Optional<LimitViolation> verifyExecutionRequestsLimits(
      final String subject, final ExecutionRequestsGloas requests, final SpecConfigGloas config) {
    return check(
            subject,
            "withdrawal requests",
            requests.getWithdrawals().size(),
            config.getMaxWithdrawalRequestsPerPayload())
        .or(
            () ->
                check(
                    subject,
                    "consolidation requests",
                    requests.getConsolidations().size(),
                    config.getMaxConsolidationRequestsPerPayload()))
        .or(
            () ->
                check(
                    subject,
                    "builder deposit requests",
                    requests.getBuilderDeposits().size(),
                    config.getMaxBuilderDepositRequestsPerPayload()))
        .or(
            () ->
                check(
                    subject,
                    "builder exit requests",
                    requests.getBuilderExits().size(),
                    config.getMaxBuilderExitRequestsPerPayload()));
  }

  /**
   * The {@code MAX_WITHDRAWALS_PER_PAYLOAD} rule of {@code
   * validate_execution_payload_envelope_gossip}.
   */
  public static Optional<LimitViolation> verifyWithdrawalsLimit(
      final String subject, final ExecutionPayload payload, final SpecConfigGloas config) {
    return check(
        subject,
        "withdrawals",
        ExecutionPayloadCapella.required(payload).getWithdrawals().size(),
        config.getMaxWithdrawalsPerPayload());
  }

  private static Optional<LimitViolation> check(
      final String subject, final String description, final int count, final int limit) {
    return count > limit
        ? Optional.of(new LimitViolation(subject, description, count, limit))
        : Optional.empty();
  }
}
