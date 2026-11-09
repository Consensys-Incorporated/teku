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

import java.util.Collections;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator.InitialStateData;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.Web3SignerNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class RemoteValidatorKeysAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldMaintainValidatorsInMutableClient() throws Exception {
    final String networkName = "swift";
    final ValidatorKeystores validatorKeystores =
        createValidatorKeyGenerator().generateValidatorKeys(8);

    final InitialStateData genesis =
        createGenesisGenerator().network(networkName).validatorKeys(validatorKeystores).generate();

    final TekuBeaconNode beaconNode =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode().withInitialState(genesis).build());
    final Web3SignerNode web3SignerNode =
        createWeb3SignerNode(config -> config.withNetwork(networkName));
    web3SignerNode.start();
    final ValidatorKeysApi signerApi = web3SignerNode.getValidatorKeysApi();

    final TekuValidatorNode validatorClient =
        createValidatorNode(
            TekuNodeConfigBuilder.createValidatorClient()
                .withValidatorApiEnabled()
                .withExternalSignerUrl(web3SignerNode.getValidatorRestApiUrl())
                .withInteropModeDisabled()
                .withBeaconNodes(beaconNode)
                .build());

    beaconNode.start();
    validatorClient.start();

    signerApi.addLocalValidatorsAndExpect(validatorKeystores, "imported");
    signerApi.assertLocalValidatorListing(validatorKeystores.getPublicKeys());

    final ValidatorKeysApi validatorNodeApi = validatorClient.getValidatorKeysApi();

    validatorNodeApi.assertLocalValidatorListing(Collections.emptyList());
    validatorNodeApi.assertRemoteValidatorListing(Collections.emptyList());

    validatorNodeApi.addRemoteValidatorsAndExpect(
        validatorKeystores.getPublicKeys(), web3SignerNode.getValidatorRestApiUrl(), "imported");

    validatorClient.waitForLogMessageContaining("Added validator");
    validatorNodeApi.assertLocalValidatorListing(Collections.emptyList());
    validatorNodeApi.assertRemoteValidatorListing(validatorKeystores.getPublicKeys());

    // add Local should see duplicates, as they're already loaded
    validatorNodeApi.addLocalValidatorsAndExpect(validatorKeystores, "duplicate");
    // second remote add should also see as duplicates
    validatorNodeApi.addRemoteValidatorsAndExpect(
        validatorKeystores.getPublicKeys(), web3SignerNode.getValidatorRestApiUrl(), "duplicate");

    validatorClient.waitForLogMessageContaining("Published block");

    // generate voluntary exit
    validatorNodeApi.generateVoluntaryExitAndCheckValidatorIndex(
        validatorKeystores.getPublicKeys().get(1), 1);

    // remove a validator
    final BLSPublicKey removedPubKey = validatorKeystores.getPublicKeys().get(0);
    validatorNodeApi.removeRemoteValidatorAndCheckStatus(removedPubKey, "deleted");

    // should only be 7 validators left
    validatorClient.waitForLogMessageContaining("Removed remote validator");
    validatorClient.waitForLogMessageContaining("Published block");
    validatorNodeApi.assertRemoteValidatorListing(validatorKeystores.getPublicKeys().subList(1, 7));

    // remove validator that doesn't exist
    validatorNodeApi.removeRemoteValidatorAndCheckStatus(removedPubKey, "not_found");

    validatorClient.stop();
    beaconNode.stop();
  }
}
