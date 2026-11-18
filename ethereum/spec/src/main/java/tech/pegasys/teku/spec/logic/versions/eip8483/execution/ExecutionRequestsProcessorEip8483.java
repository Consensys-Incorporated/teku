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

package tech.pegasys.teku.spec.logic.versions.eip8483.execution;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigEip8483;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.BuilderPqRegistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ValidatorPqRegistrationRequest;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip8483.MutableBeaconStateEip8483;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.BuilderPqRegistration;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.ValidatorPqRegistration;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.logic.versions.gloas.execution.ExecutionRequestsProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ValidatorsUtilGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip8483;

/**
 * Processes the EIP-8483 PQ registration requests added in EIP-8483.
 *
 * <p>The spec checks each request against every existing registration with linear scans. To avoid
 * an O(requests * registrations) cost per payload, each batch builds lookup sets over the existing
 * registrations once and keeps them up to date as requests are applied. The order and outcome of
 * every check match the spec.
 */
public class ExecutionRequestsProcessorEip8483 extends ExecutionRequestsProcessorGloas {

  private static final int XMSS_PUBKEY_SIZE = 32;

  private final SchemaDefinitionsEip8483 schemaDefinitionsEip8483;
  private final BeaconStateAccessorsGloas beaconStateAccessorsEip8483;
  private final PredicatesGloas predicatesEip8483;

  public ExecutionRequestsProcessorEip8483(
      final SchemaDefinitionsEip8483 schemaDefinitions,
      final MiscHelpersGloas miscHelpers,
      final SpecConfigEip8483 specConfig,
      final PredicatesGloas predicates,
      final ValidatorsUtilGloas validatorsUtil,
      final BeaconStateMutatorsGloas beaconStateMutators,
      final BeaconStateAccessorsGloas beaconStateAccessors) {
    super(
        schemaDefinitions,
        miscHelpers,
        specConfig,
        predicates,
        validatorsUtil,
        beaconStateMutators,
        beaconStateAccessors);
    this.schemaDefinitionsEip8483 = schemaDefinitions;
    this.beaconStateAccessorsEip8483 = beaconStateAccessors;
    this.predicatesEip8483 = predicates;
  }

  // process_validator_pq_registration_request
  @Override
  public void processValidatorPqRegistrationRequests(
      final MutableBeaconState state, final List<ValidatorPqRegistrationRequest> requests) {
    if (requests.isEmpty()) {
      return;
    }
    final SszMutableList<ValidatorPqRegistration> registrations =
        MutableBeaconStateEip8483.required(state).getValidatorPqRegistrations();

    final Set<Bytes32> registeredXmssPubkeys = new HashSet<>();
    final Set<Bytes32> registeredVrfData = new HashSet<>();
    final Map<UInt64, Integer> registrationPositionByValidator = new HashMap<>();
    for (int i = 0; i < registrations.size(); i++) {
      final ValidatorPqRegistration registration = registrations.get(i);
      registeredXmssPubkeys.add(xmssPubkey(registration.getPqData()));
      registeredVrfData.add(vrfData(registration.getPqData()));
      registrationPositionByValidator.put(registration.getValidatorIndex(), i);
    }

    for (final ValidatorPqRegistrationRequest request : requests) {
      // Verify the validator exists
      final Optional<Integer> maybeValidatorIndex =
          validatorsUtil.getValidatorIndex(state, request.getValidatorPubkey());
      if (maybeValidatorIndex.isEmpty()) {
        continue;
      }
      final UInt64 validatorIndex = UInt64.valueOf(maybeValidatorIndex.get());
      final Validator validator = state.getValidators().get(maybeValidatorIndex.get());

      // Verify the validator has execution withdrawal credentials
      if (!predicatesEip8483.hasExecutionWithdrawalCredential(validator)) {
        continue;
      }

      // Verify the request comes from the validator's withdrawal address
      if (!validator
          .getWithdrawalCredentials()
          .slice(12)
          .equals(request.getSourceAddress().getWrappedBytes())) {
        continue;
      }

      final Bytes pqData = request.getPqData();
      final Bytes32 xmssPubkey = xmssPubkey(pqData);
      final Bytes32 vrfData = vrfData(pqData);
      // Verify the XMSS public key is not already registered
      if (registeredXmssPubkeys.contains(xmssPubkey)) {
        continue;
      }
      // Verify the VRF data is not already registered
      if (registeredVrfData.contains(vrfData)) {
        continue;
      }

      final ValidatorPqRegistration newRegistration =
          schemaDefinitionsEip8483
              .getValidatorPqRegistrationSchema()
              .create(validatorIndex, pqData);
      final Integer existingPosition = registrationPositionByValidator.get(validatorIndex);
      if (existingPosition != null) {
        // Overwrite an existing registration for this validator
        final Bytes oldPqData = registrations.get(existingPosition).getPqData();
        registeredXmssPubkeys.remove(xmssPubkey(oldPqData));
        registeredVrfData.remove(vrfData(oldPqData));
        registrations.set(existingPosition, newRegistration);
      } else {
        // Otherwise append a new registration
        registrations.append(newRegistration);
        registrationPositionByValidator.put(validatorIndex, registrations.size() - 1);
      }
      registeredXmssPubkeys.add(xmssPubkey);
      registeredVrfData.add(vrfData);
    }
  }

  // process_builder_pq_registration_request
  @Override
  public void processBuilderPqRegistrationRequests(
      final MutableBeaconState state, final List<BuilderPqRegistrationRequest> requests) {
    if (requests.isEmpty()) {
      return;
    }
    final MutableBeaconStateEip8483 stateEip8483 = MutableBeaconStateEip8483.required(state);
    final SszMutableList<BuilderPqRegistration> registrations =
        stateEip8483.getBuilderPqRegistrations();

    final Set<Bytes32> registeredXmssPubkeys = new HashSet<>();
    final Map<BLSPublicKey, Integer> registrationPositionByBuilder = new HashMap<>();
    for (int i = 0; i < registrations.size(); i++) {
      final BuilderPqRegistration registration = registrations.get(i);
      registeredXmssPubkeys.add(registration.getPqData());
      registrationPositionByBuilder.put(registration.getBuilderPubkey(), i);
    }

    for (final BuilderPqRegistrationRequest request : requests) {
      // Verify the builder exists
      final BLSPublicKey builderPubkey = request.getBuilderPubkey();
      final Optional<Integer> maybeBuilderIndex =
          beaconStateAccessorsEip8483.getBuilderIndex(state, builderPubkey);
      if (maybeBuilderIndex.isEmpty()) {
        continue;
      }
      final Builder builder = stateEip8483.getBuilders().get(maybeBuilderIndex.get());

      // Verify the request comes from the builder's execution address
      if (!builder
          .getExecutionAddress()
          .getWrappedBytes()
          .equals(request.getSourceAddress().getWrappedBytes())) {
        continue;
      }

      // Verify the XMSS public key is not already registered
      final Bytes32 xmssPubkey = request.getPqData();
      if (registeredXmssPubkeys.contains(xmssPubkey)) {
        continue;
      }

      final BuilderPqRegistration newRegistration =
          schemaDefinitionsEip8483
              .getBuilderPqRegistrationSchema()
              .create(builderPubkey, xmssPubkey);
      final Integer existingPosition = registrationPositionByBuilder.get(builderPubkey);
      if (existingPosition != null) {
        // Overwrite an existing registration for this builder
        registeredXmssPubkeys.remove(registrations.get(existingPosition).getPqData());
        registrations.set(existingPosition, newRegistration);
      } else {
        // Otherwise append a new registration
        registrations.append(newRegistration);
        registrationPositionByBuilder.put(builderPubkey, registrations.size() - 1);
      }
      registeredXmssPubkeys.add(xmssPubkey);
    }
  }

  private static Bytes32 xmssPubkey(final Bytes pqData) {
    return Bytes32.wrap(pqData.slice(0, XMSS_PUBKEY_SIZE));
  }

  private static Bytes32 vrfData(final Bytes pqData) {
    return Bytes32.wrap(pqData.slice(XMSS_PUBKEY_SIZE));
  }
}
