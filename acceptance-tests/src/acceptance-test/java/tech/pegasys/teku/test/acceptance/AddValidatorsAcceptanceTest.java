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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator.InitialStateData;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class AddValidatorsAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldLoadAdditionalValidatorsWithoutRestart() throws Exception {
    final String networkName = "swift";

    final ValidatorKeystores initialKeystores =
        createValidatorKeyGenerator().generateValidatorKeys(2);

    final ValidatorKeystores additionalKeystores =
        createValidatorKeyGenerator().generateValidatorKeys(2);

    final InitialStateData genesis =
        createGenesisGenerator()
            .network(networkName)
            .validatorKeys(initialKeystores, additionalKeystores)
            .generate();

    final TekuBeaconNode node =
        createTekuBeaconNode(
            TekuNodeConfigBuilder.createBeaconNode()
                .withNetwork(networkName)
                .withInitialState(genesis)
                .withReadOnlyKeystorePath(initialKeystores)
                .build());
    node.start();

    node.waitForOwnedValidatorCount(2);
    node.waitForGenesis();

    node.addValidators(additionalKeystores);
    node.waitForOwnedValidatorCount(4);

    // If the added validators perform their duties properly, the network will finalize.
    node.waitForNewFinalization();

    // Check loading new validators a second time still works and that they don't have to be active
    final ValidatorKeystores evenMoreKeystores =
        createValidatorKeyGenerator().generateValidatorKeys(1);
    node.addValidators(evenMoreKeystores);
    node.waitForOwnedValidatorCount(5);
  }
}
