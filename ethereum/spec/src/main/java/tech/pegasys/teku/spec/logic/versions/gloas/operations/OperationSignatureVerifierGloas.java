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

package tech.pegasys.teku.spec.logic.versions.gloas.operations;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;

public class OperationSignatureVerifierGloas extends OperationSignatureVerifier {

  private final MiscHelpersGloas miscHelpersGloas;
  private final PredicatesGloas predicatesGloas;

  public OperationSignatureVerifierGloas(
      final MiscHelpersGloas miscHelpers,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final PredicatesGloas predicates) {
    super(miscHelpers, beaconStateAccessors);
    this.miscHelpersGloas = miscHelpers;
    this.predicatesGloas = predicates;
  }

  @Override
  public boolean verifyVoluntaryExitSignature(
      final BeaconState state,
      final SignedVoluntaryExit signedExit,
      final BLSSignatureVerifier signatureVerifier) {
    final VoluntaryExit exit = signedExit.getMessage();
    final UInt64 validatorIndex = exit.getValidatorIndex();
    final Bytes32 domain =
        beaconStateAccessors.getVoluntaryExitDomain(
            exit.getEpoch(), state.getFork(), state.getGenesisValidatorsRoot());
    final Bytes signingRoot = miscHelpersGloas.computeSigningRoot(exit, domain);
    final Optional<BLSPublicKey> maybePubkey;
    if (predicatesGloas.isBuilderIndex(validatorIndex)) {
      final UInt64 builderIndex =
          miscHelpersGloas.convertValidatorIndexToBuilderIndex(validatorIndex);
      maybePubkey = beaconStateAccessors.getBuilderPubKey(state, builderIndex);
    } else {
      maybePubkey = beaconStateAccessors.getValidatorPubKey(state, validatorIndex);
    }
    return maybePubkey
        .map(pubkey -> signatureVerifier.verify(pubkey, signingRoot, signedExit.getSignature()))
        .orElse(false);
  }
}
