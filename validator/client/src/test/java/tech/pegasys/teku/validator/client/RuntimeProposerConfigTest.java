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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderConfig;
import tech.pegasys.teku.validator.client.restapi.apis.schema.BuilderEntry;

class RuntimeProposerConfigTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private RuntimeProposerConfig proposerConfig = new RuntimeProposerConfig(Optional.empty());
  private final BLSPublicKey pubkey = dataStructureUtil.randomPublicKey();
  private final BLSPublicKey pubkey2 = dataStructureUtil.randomPublicKey();
  private final Eth1Address address = dataStructureUtil.randomEth1Address();
  private final Eth1Address address2 = dataStructureUtil.randomEth1Address();
  private final UInt64 gasLimit = dataStructureUtil.randomUInt64();
  private final UInt64 gasLimit2 = dataStructureUtil.randomUInt64();

  @Test
  void shouldAddFeeRecipientEntries() {
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).isEmpty();
    proposerConfig.updateFeeRecipient(pubkey, address);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address);
  }

  @Test
  void shouldAddGasLimitEntries() {
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey)).isEmpty();
    proposerConfig.updateGasLimit(pubkey, gasLimit);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey)).contains(gasLimit);
  }

  @Test
  void shouldUpdateFeeRecipientEntries() {
    proposerConfig.updateFeeRecipient(pubkey, address);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address);
    proposerConfig.updateFeeRecipient(pubkey, address2);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address2);
  }

  @Test
  void shouldUpdateGasLimitEntries() {
    proposerConfig.updateGasLimit(pubkey, gasLimit);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey)).contains(gasLimit);
    proposerConfig.updateGasLimit(pubkey, gasLimit2);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey)).contains(gasLimit2);
  }

  @Test
  void shouldDeleteFeeRecipientEntries() {
    proposerConfig.updateFeeRecipient(pubkey, address);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address);
    proposerConfig.deleteFeeRecipient(pubkey);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).isEmpty();
    proposerConfig.deleteFeeRecipient(pubkey2);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey2)).isEmpty();
    proposerConfig.updateGasLimit(pubkey2, gasLimit2);
    proposerConfig.deleteFeeRecipient(pubkey2);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey2)).isEmpty();
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey2)).contains(gasLimit2);
  }

  @Test
  void shouldDeleteGasLimitEntries() {
    proposerConfig.updateGasLimit(pubkey, gasLimit);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey)).contains(gasLimit);
    proposerConfig.deleteGasLimit(pubkey);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey)).isEmpty();
    proposerConfig.deleteGasLimit(pubkey2);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey2)).isEmpty();
    proposerConfig.updateFeeRecipient(pubkey2, address2);
    proposerConfig.deleteGasLimit(pubkey2);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey2)).isEmpty();
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey2)).contains(address2);
  }

  @Test
  void shouldDeleteEntryFromConfigFile(@TempDir final Path tempDir) throws IOException {
    final Path testData = tempDir.resolve("test");
    proposerConfig = new RuntimeProposerConfig(Optional.of(testData));
    proposerConfig.updateFeeRecipient(pubkey, address);
    proposerConfig.updateGasLimit(pubkey, gasLimit);
    final String initialData = Files.readString(testData);
    assertThat(initialData)
        .isEqualTo(
            "{\"%s\":{\"fee_recipient\":\"%s\",\"gas_limit\":\"%s\"}}", pubkey, address, gasLimit);
    proposerConfig.deleteFeeRecipient(pubkey);
    proposerConfig.deleteGasLimit(pubkey);
    final String data = Files.readString(testData);
    assertThat(data).isEqualTo("{}");
  }

  @Test
  void shouldRejectBuilderConfigWithDuplicateUrls() {
    final BuilderEntry builder =
        new BuilderEntry(
            "https://builder.example.com",
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final BuilderConfig builderConfig =
        new BuilderConfig(
            Optional.empty(), Optional.empty(), Optional.of(List.of(builder, builder)));

    assertThatThrownBy(
            () ->
                new RuntimeProposerConfig.RuntimeConfigBuilder()
                    .builderConfig(Optional.of(builderConfig))
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("builders contain duplicate url");
  }

  @Test
  void shouldFailIfSourceFileIsInvalid(@TempDir final Path tempDir) throws IOException {
    final Path testData = tempDir.resolve("test");
    Files.writeString(testData, "thisIsInvalidJson");
    assertThatThrownBy(() -> new RuntimeProposerConfig(Optional.of(testData)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldSave(@TempDir final Path tempDir) throws IOException {
    final Path testData = tempDir.resolve("test");
    proposerConfig = new RuntimeProposerConfig(Optional.of(testData));
    proposerConfig.updateFeeRecipient(pubkey, address);
    proposerConfig.updateGasLimit(pubkey, gasLimit);
    proposerConfig.updateFeeRecipient(pubkey2, address2);
    proposerConfig.updateGasLimit(pubkey2, gasLimit2);
    final String data = Files.readString(testData);
    assertThat(data)
        .isEqualTo(
            String.format(
                "{\"%s\":{\"fee_recipient\":\"%s\",\"gas_limit\":\"%s\"},\"%s\":{\"fee_recipient\":\"%s\",\"gas_limit\":\"%s\"}}",
                pubkey, address, gasLimit, pubkey2, address2, gasLimit2));
  }

  @Test
  void shouldSaveFeeRecipientAndGasLimitSolelyAndSeparately(@TempDir final Path tempDir)
      throws IOException {
    final Path testData = tempDir.resolve("test");
    proposerConfig = new RuntimeProposerConfig(Optional.of(testData));
    proposerConfig.updateFeeRecipient(pubkey, address);
    proposerConfig.updateGasLimit(pubkey2, gasLimit2);
    final String data = Files.readString(testData);
    assertThat(data)
        .isEqualTo(
            String.format(
                "{\"%s\":{\"fee_recipient\":\"%s\"},\"%s\":{\"gas_limit\":\"%s\"}}",
                pubkey, address, pubkey2, gasLimit2));
  }

  @Test
  void shouldLoadOnCreation(@TempDir final Path tempDir) throws IOException {
    final Path testData = tempDir.resolve("test");
    Files.writeString(
        testData,
        String.format(
            "{\"%s\":{\"fee_recipient\":\"%s\",\"gas_limit\":\"%s\"},\"%s\":{\"fee_recipient\":\"%s\",\"gas_limit\":\"%s\"}}",
            pubkey, address, gasLimit, pubkey2, address2, gasLimit2),
        StandardCharsets.UTF_8);
    proposerConfig = new RuntimeProposerConfig(Optional.of(testData));
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey)).contains(address);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey)).contains(gasLimit);
    assertThat(proposerConfig.getEth1AddressForPubKey(pubkey2)).contains(address2);
    assertThat(proposerConfig.getGasLimitForPubKey(pubkey2)).contains(gasLimit2);
  }
}
