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

package tech.pegasys.teku.validator.client;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.data.SlashingProtectionImporter;
import tech.pegasys.teku.validator.client.doppelganger.DoppelgangerDetector;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysResponse;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteRemoteKeysResponse;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ExternalValidator;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;
import tech.pegasys.teku.validator.client.slashingriskactions.SlashingRiskAction;

public interface KeyManager {
  List<Validator> getLocalValidatorKeys();

  List<ExternalValidator> getRemoteValidatorKeys();

  Optional<Validator> getValidatorByPublicKey(final BLSPublicKey publicKey);

  DeleteKeysResponse deleteValidators(
      final List<BLSPublicKey> validators, final Path slashingProtectionPath);

  DeleteRemoteKeysResponse deleteExternalValidators(final List<BLSPublicKey> validators);

  List<PostKeyResult> importValidators(
      final List<String> keystores,
      final List<String> passwords,
      final Optional<SlashingProtectionImporter> slashingProtectionImporter,
      final Optional<DoppelgangerDetector> doppelgangerDetector,
      final SlashingRiskAction doppelgangerDetectionAction);

  List<PostKeyResult> importExternalValidators(
      final List<ExternalValidator> validators,
      final Optional<DoppelgangerDetector> doppelgangerDetector,
      final SlashingRiskAction doppelgangerDetectionAction);
}
