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

package tech.pegasys.teku.test.acceptance;

import com.google.common.io.Resources;
import java.net.URL;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator.InitialStateData;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class LocalValidatorKeysAcceptanceTest extends AcceptanceTestBase {
  private static final URL JWT_FILE = Resources.getResource("auth/ee-jwt-secret.hex");

  @Test
  void shouldMaintainValidatorsInMutableClient() throws Exception {
    final String networkName = "swift";

    final ValidatorKeystores validatorKeystores =
        createValidatorKeyGenerator().generateValidatorKeys(8);
    final ValidatorKeystores extraKeys = createValidatorKeyGenerator().generateValidatorKeys(1);

    final InitialStateData genesis =
        createGenesisGenerator()
            .network(networkName)
            .withAltairEpoch(UInt64.ZERO)
            .withBellatrixEpoch(UInt64.ZERO)
            .validatorKeys(validatorKeystores, extraKeys)
            .generate();

    final String defaultFeeRecipient = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73";
    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withStubExecutionEngine()
                .withJwtSecretFile(JWT_FILE)
                .withNetwork(networkName)
                .withInitialState(genesis)
                .withAltairEpoch(UInt64.ZERO)
                .withBellatrixEpoch(UInt64.ZERO)
                .withTerminalBlockHash(DEFAULT_EL_GENESIS_HASH, 0)
                .withValidatorProposerDefaultFeeRecipient(defaultFeeRecipient)
                .build());
    final TekuValidatorNode validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withValidatorApiEnabled()
                .withValidatorProposerDefaultFeeRecipient(defaultFeeRecipient)
                .withInteropModeDisabled()
                .withBeaconNodes(beaconNode)
                .withNetwork("auto")
                .build());
    final ValidatorKeysApi api = validatorClient.getValidatorKeysApi();

    beaconNode.start();
    validatorClient.start();

    api.assertLocalValidatorListing(Collections.emptyList());

    api.addLocalValidatorsAndExpect(validatorKeystores, "imported");

    validatorClient.waitForLogMessageContaining("Added validator");
    validatorClient.waitForLogMessageContaining("Published block");
    api.assertLocalValidatorListing(validatorKeystores.getPublicKeys());

    api.assertValidatorGasLimit(
        validatorKeystores.getPublicKeys().get(1), UInt64.valueOf(60_000_000));

    // generate voluntary exit
    api.generateVoluntaryExitAndCheckValidatorIndex(validatorKeystores.getPublicKeys().get(1), 1);

    api.assertValidatorFeeRecipient(validatorKeystores.getPublicKeys().get(1), defaultFeeRecipient);

    final String expectedFeeRecipient = "0xAbcF8e0d4e9587369b2301D0790347320302cc09";
    api.addFeeRecipient(
        validatorKeystores.getPublicKeys().get(0), Eth1Address.fromHexString(expectedFeeRecipient));
    api.assertValidatorFeeRecipient(
        validatorKeystores.getPublicKeys().get(0), expectedFeeRecipient);
    final UInt64 expectedGasLimit = UInt64.valueOf(1234567);
    api.addGasLimit(validatorKeystores.getPublicKeys().get(0), expectedGasLimit);
    api.assertValidatorGasLimit(validatorKeystores.getPublicKeys().get(0), expectedGasLimit);

    // second add attempt would be duplicates
    api.addLocalValidatorsAndExpect(validatorKeystores, "duplicate");

    // a random key won't be found, remove should give not_found
    api.removeLocalValidatorAndCheckStatus(extraKeys.getPublicKeys().get(0), "not_found");

    // Wait for a full epoch to pass so that all validators have attested
    // This ensures they have all generated slashing protection data
    beaconNode.waitForNextEpoch();
    beaconNode.waitForNextEpoch();

    // remove a validator
    final BLSPublicKey removedPubkey = validatorKeystores.getPublicKeys().get(0);
    api.removeLocalValidatorAndCheckStatus(removedPubkey, "deleted");

    // should only be 7 validators left
    validatorClient.waitForLogMessageContaining("Removed validator");
    validatorClient.waitForLogMessageContaining("Published block");
    api.assertLocalValidatorListing(validatorKeystores.getPublicKeys().subList(1, 7));

    // remove the same validator again
    api.removeLocalValidatorAndCheckStatus(removedPubkey, "not_active");
  }
}
