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

package tech.pegasys.teku.spec.logic.versions.gloas.util;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.logic.common.util.ProposerPreferencesUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class ProposerPreferencesUtilGloas implements ProposerPreferencesUtil {

  private final SchemaDefinitionsGloas schemaDefinitions;

  public ProposerPreferencesUtilGloas(final SchemaDefinitionsGloas schemaDefinitions) {
    this.schemaDefinitions = schemaDefinitions;
  }

  @Override
  public Optional<ProposerPreferences> createProposerPreferences(
      final Bytes32 dependentRoot,
      final UInt64 slot,
      final UInt64 validatorIndex,
      final Eth1Address feeRecipient,
      final UInt64 targetGasLimit) {
    return Optional.of(
        schemaDefinitions
            .getProposerPreferencesSchema()
            .create(dependentRoot, slot, validatorIndex, feeRecipient, targetGasLimit));
  }

  @Override
  public Optional<SignedProposerPreferences> createSignedProposerPreferences(
      final ProposerPreferences preferences, final BLSSignature signature) {
    return Optional.of(
        schemaDefinitions.getSignedProposerPreferencesSchema().create(preferences, signature));
  }
}
