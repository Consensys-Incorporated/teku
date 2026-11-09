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

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;

public class BlsToExecutionChangeGenerator {

  private final Spec spec;
  private final Bytes32 genesisValidatorRoot;

  public BlsToExecutionChangeGenerator(final Spec spec, final Bytes32 genesisValidatorRoot) {
    this.spec = spec;
    this.genesisValidatorRoot = genesisValidatorRoot;
  }

  public SignedBlsToExecutionChange createAndSign(
      final UInt64 validatorIndex,
      final BLSKeyPair validatorCredentials,
      final Bytes20 executionAddress,
      final UInt64 epoch) {
    final SchemaDefinitionsCapella schemaDefinitionsCapella =
        SchemaDefinitionsCapella.required(spec.atEpoch(epoch).getSchemaDefinitions());

    final BlsToExecutionChange blsToExecutionChange =
        schemaDefinitionsCapella
            .getBlsToExecutionChangeSchema()
            .create(validatorIndex, validatorCredentials.getPublicKey(), executionAddress);

    final BLSSignature signature =
        BLS.sign(
            validatorCredentials.getSecretKey(),
            signingRootForBlsToExecutionChanges(blsToExecutionChange, epoch));

    return schemaDefinitionsCapella
        .getSignedBlsToExecutionChangeSchema()
        .create(blsToExecutionChange, signature);
  }

  public SszList<SignedBlsToExecutionChange> asSszList(
      final UInt64 epoch, final SignedBlsToExecutionChange... changes) {
    return BeaconBlockBodySchemaCapella.required(
            spec.atEpoch(epoch).getSchemaDefinitions().getBeaconBlockBodySchema())
        .getBlsToExecutionChangesSchema()
        .createFromElements(List.of(changes));
  }

  private Bytes signingRootForBlsToExecutionChanges(
      final BlsToExecutionChange blsToExecutionChange, final UInt64 epoch) {
    final Bytes32 domain =
        spec.atEpoch(epoch)
            .miscHelpers()
            .computeDomain(Domain.BLS_TO_EXECUTION_CHANGE, genesisValidatorRoot);
    final SpecVersion specVersion = spec.atEpoch(epoch);
    return specVersion.miscHelpers().computeSigningRoot(blsToExecutionChange, domain);
  }
}
