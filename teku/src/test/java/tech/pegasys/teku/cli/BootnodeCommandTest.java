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

package tech.pegasys.teku.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static tech.pegasys.teku.spec.networks.Eth2Network.EPHEMERY;

import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.KeyType;
import io.libp2p.core.crypto.PrivKey;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.spec.networks.Eth2Network;

class BootnodeCommandTest extends AbstractBeaconNodeCommandTest {

  @BeforeEach
  void setUp() {
    expectedNodeMode = NodeMode.BOOTNODE_ONLY;
  }

  @Test
  public void shouldDefaultToMainnet() {
    final String[] args = new String[] {"bootnode"};
    int parseResult = beaconNodeCommand.parse(args);

    assertThat(parseResult).isEqualTo(0);

    final TekuConfiguration tekuConfig = getResultingTekuConfiguration();

    assertThat(tekuConfig.eth2NetworkConfiguration().getEth2Network())
        .hasValue(Eth2Network.MAINNET);
  }

  @ParameterizedTest
  @EnumSource(Eth2Network.class)
  public void shouldAllowChangingNetwork(final Eth2Network network) {
    // ephemery network doesn't use internal configuration, so this test doesn't exercise it
    assumeThat(network).isNotEqualTo(EPHEMERY);
    final String[] args = new String[] {"bootnode", "--network", network.name()};
    int parseResult = beaconNodeCommand.parse(args);

    assertThat(parseResult).isEqualTo(0);

    final TekuConfiguration tekuConfig = getResultingTekuConfiguration();

    assertThat(tekuConfig.eth2NetworkConfiguration().getEth2Network()).hasValue(network);
  }

  @Test
  public void shouldUseBootnodeDataDir(@TempDir final Path tempDir) {
    final String[] args = new String[] {"bootnode", "--data-base-path=" + tempDir.toAbsolutePath()};
    int parseResult = beaconNodeCommand.parse(args);

    assertThat(parseResult).isEqualTo(0);

    final TekuConfiguration tekuConfig = getResultingTekuConfiguration();

    assertThat(tekuConfig.dataConfig().getDataBasePath()).isEqualTo(tempDir.toAbsolutePath());
  }

  @Test
  public void shouldCorrectlyAcceptRelevantConfig(@TempDir final Path tempDir) {
    final Path keyAbsPath = tempDir.resolve("new-node-key");
    final String privKey = createPrivateKey(keyAbsPath);
    final String[] args =
        new String[] {
          "bootnode",
          "--p2p-port",
          "9123",
          "--p2p-advertised-ip",
          "192.168.9.9",
          "--p2p-private-key-file",
          keyAbsPath.toAbsolutePath().toString()
        };

    int parseResult = beaconNodeCommand.parse(args);

    assertThat(parseResult).isEqualTo(0);

    final TekuConfiguration tekuConfig = getResultingTekuConfiguration();
    final NetworkConfig networkConfig = tekuConfig.p2p().getNetworkConfig();
    assertThat(networkConfig.getListenPort()).isEqualTo(9123);
    assertThat(networkConfig.getAdvertisedPort()).isEqualTo(9123);
    assertThat(networkConfig.getAdvertisedIps()).contains("192.168.9.9");
    assertThat(networkConfig.getPrivateKeySource()).isPresent();
    assertThat(networkConfig.getPrivateKeySource().get().getPrivateKeyBytes().toHexString())
        .isEqualTo(privKey);
  }

  private String createPrivateKey(final Path path) {
    final PrivKey privKey = KeyKt.generateKeyPair(KeyType.SECP256K1).component1();
    final Bytes privKeyBytes = Bytes.wrap(KeyKt.marshalPrivateKey(privKey));

    try {
      Files.createFile(path);
      Files.writeString(path, privKeyBytes.toHexString(), Charset.defaultCharset());
      return privKeyBytes.toHexString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
