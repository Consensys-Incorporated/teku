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

package tech.pegasys.teku.cli.options;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.cli.OSUtils;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;

public class Eth2P2PNetworkOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadFromConfigurationFile() {
    final Eth2NetworkConfiguration eth2NetworkConfig =
        Eth2NetworkConfiguration.builder("holesky").build();
    final TekuConfiguration config = getTekuConfigurationFromFile("networkOptions_config.yaml");
    assertThat(config.eth2NetworkConfiguration()).isEqualTo(eth2NetworkConfig);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"mainnet", "minimal", "swift", "holesky"})
  public void useDefaultsFromNetworkDefinition(final String networkName) {
    final Eth2NetworkConfiguration eth2NetworkConfig =
        Eth2NetworkConfiguration.builder(networkName).build();

    beaconNodeCommand.parse(new String[] {"--network", networkName});
    final TekuConfiguration tekuConfig = getResultingTekuConfiguration();

    // eth2Config
    assertThat(tekuConfig.eth2NetworkConfiguration()).isEqualTo(eth2NetworkConfig);

    // Storage config
    assertThat(tekuConfig.storageConfiguration().getEth1DepositContract())
        .isEqualTo(eth2NetworkConfig.getEth1DepositContractAddress());

    // WS config
    assertThat(tekuConfig.eth2NetworkConfiguration().getNetworkBoostrapConfig().getInitialState())
        .isEqualTo(eth2NetworkConfig.getNetworkBoostrapConfig().getInitialState());

    // p2p config
    assertThat(tekuConfig.discovery().getBootnodes())
        .isEqualTo(eth2NetworkConfig.getDiscoveryBootnodes());

    // Rest api
    assertThat(tekuConfig.beaconChain().beaconRestApiConfig().getEth1DepositContractAddress())
        .isEqualTo(eth2NetworkConfig.getEth1DepositContractAddress());

    // Powchain
    assertThat(tekuConfig.powchain().getDepositContract())
        .isEqualTo(eth2NetworkConfig.getEth1DepositContractAddress());
    assertThat(tekuConfig.powchain().getDepositContractDeployBlock())
        .isEqualTo(eth2NetworkConfig.getEth1DepositContractDeployBlock());
  }

  @Test
  public void overrideDepositContract() {
    beaconNodeCommand.parse(
        new String[] {
          "--network",
          "mainnet",
          "--eth1-deposit-contract-address",
          "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"
        });

    final TekuConfiguration tekuConfiguration = getResultingTekuConfiguration();
    final Eth1Address configuredDepositContract =
        tekuConfiguration.eth2NetworkConfiguration().getEth1DepositContractAddress();
    assertThat(configuredDepositContract)
        .isEqualTo(Eth1Address.fromHexString("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"));
  }

  @Test
  public void overrideDepositContractDeployBlock() {
    beaconNodeCommand.parse(
        new String[] {
          "--network", "mainnet", "--Xeth1-deposit-contract-deploy-block-override", "345"
        });

    final TekuConfiguration tekuConfiguration = getResultingTekuConfiguration();
    final Eth2NetworkConfiguration eth2NetworkConfiguration =
        tekuConfiguration.eth2NetworkConfiguration();
    final Optional<UInt64> configuredDepositContractDeployBlock =
        eth2NetworkConfiguration.getEth1DepositContractDeployBlock();
    assertThat(configuredDepositContractDeployBlock).isEqualTo(Optional.of(UInt64.valueOf(345L)));
  }

  @Test
  public void overrideDefaultBootnodesWithEmptyList() {
    beaconNodeCommand.parse(new String[] {"--network", "holesky", "--p2p-discovery-bootnodes"});

    TekuConfiguration tekuConfiguration = getResultingTekuConfiguration();
    final List<String> bootnodes = tekuConfiguration.discovery().getBootnodes();
    assertThat(bootnodes).isEmpty();
  }

  @Test
  public void usingNetworkFromUrl() {
    final URL url =
        getClass().getClassLoader().getResource("tech/pegasys/teku/cli/options/constants.yaml");
    beaconNodeCommand.parse(new String[] {"--network", url.toString()});
    final TekuConfiguration config = getResultingTekuConfiguration();
    assertThat(config.eth2NetworkConfiguration()).isNotNull();
  }

  @Test
  public void setPeerBlocksRateLimit() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xpeer-blocks-rate-limit", "10");
    final P2PConfig config = tekuConfiguration.beaconChain().p2pConfig();
    assertThat(config.getPeerBlocksRateLimit()).isEqualTo(10);
  }

  @Test
  public void setPeerBlobSidecarsRateLimit() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xpeer-blob-sidecars-rate-limit", "10");
    final P2PConfig config = tekuConfiguration.beaconChain().p2pConfig();
    assertThat(config.getPeerBlobSidecarsRateLimit()).isEqualTo(10);
  }

  @Test
  public void setPeerRequestLimit() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xpeer-request-limit", "10");
    final P2PConfig config = tekuConfiguration.beaconChain().p2pConfig();
    assertThat(config.getPeerRequestLimit()).isEqualTo(10);
  }

  @Test
  public void helpDisplaysDefaultNetwork() {
    beaconNodeCommand.parse(new String[] {"--help"});
    final String output = getCommandLineOutput();
    assertThat(output)
        .contains(
            "-n, --network=<NETWORK>    Represents which network to use."
                + OSUtils.CR
                + "                               Default: mainnet");
  }

  @Test
  public void initialState_shouldAcceptValue() {
    final String state = "state.ssz";
    final TekuConfiguration config = getTekuConfigurationFromArguments("--initial-state", state);
    assertThat(config.eth2NetworkConfiguration().getNetworkBoostrapConfig().getInitialState())
        .contains(state);
  }

  @Test
  public void initialState_shouldDefaultToNetworkValue() {
    final String network = "holesky";
    final Eth2NetworkConfiguration networkConfig =
        Eth2NetworkConfiguration.builder(network).build();
    assertThat(networkConfig.getNetworkBoostrapConfig().getInitialState()).isPresent();

    final TekuConfiguration config = getTekuConfigurationFromArguments("--network", network);
    assertThat(config.eth2NetworkConfiguration().getNetworkBoostrapConfig().getInitialState())
        .isEqualTo(networkConfig.getNetworkBoostrapConfig().getInitialState());
    assertThat(
            config
                .eth2NetworkConfiguration()
                .getNetworkBoostrapConfig()
                .isUsingCustomInitialState())
        .isFalse();
  }

  @Test
  public void initialState_shouldOverrideNetworkValue() {
    final String state = "state.ssz";
    final String network = "holesky";
    final Eth2NetworkConfiguration networkConfig =
        Eth2NetworkConfiguration.builder(network).build();
    assertThat(networkConfig.getNetworkBoostrapConfig().getInitialState()).isPresent();

    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--initial-state", state, "--network", network);
    assertThat(config.eth2NetworkConfiguration().getNetworkBoostrapConfig().getInitialState())
        .contains(state);
    assertThat(
            config
                .eth2NetworkConfiguration()
                .getNetworkBoostrapConfig()
                .isUsingCustomInitialState())
        .isTrue();
  }

  @Test
  public void initialState_shouldDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    final Optional<String> defaultState =
        config.eth2NetworkConfiguration().getNetworkBoostrapConfig().getInitialState();
    assertThat(config.eth2NetworkConfiguration().getNetworkBoostrapConfig().getInitialState())
        .isEqualTo(defaultState);
    assertThat(
            config
                .eth2NetworkConfiguration()
                .getNetworkBoostrapConfig()
                .isUsingCustomInitialState())
        .isFalse();
  }
}
