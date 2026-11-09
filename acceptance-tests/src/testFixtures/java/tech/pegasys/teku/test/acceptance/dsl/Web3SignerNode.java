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

package tech.pegasys.teku.test.acceptance.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;

public class Web3SignerNode extends Node {

  private static final Logger LOG = LogManager.getLogger();
  private static final int HTTP_API_PORT = 9000;
  private final Web3SignerNode.Config config;
  private boolean started = false;
  private final ValidatorKeysApi validatorKeysApi =
      new ValidatorKeysApi(new SimpleHttpClient(), this::getSignerUrl, this::getApiPassword);
  private Set<File> configFiles;

  private Web3SignerNode(final Network network, final Web3SignerNode.Config config) {
    super(network, "consensys/web3signer:develop", LOG);
    container
        .withExposedPorts(HTTP_API_PORT)
        .withLogConsumer(frame -> LOG.debug(frame.getUtf8String().trim()))
        .waitingFor(new HttpWaitStrategy().forPort(HTTP_API_PORT).forPath("/upcheck"))
        .withCommand("--config-file=" + CONFIG_FILE_PATH, "eth2");
    this.config = config;
  }

  public static Web3SignerNode create(
      final Network network, final Consumer<Web3SignerNode.Config> configOptions) {

    final Web3SignerNode.Config config = new Web3SignerNode.Config();
    configOptions.accept(config);

    return new Web3SignerNode(network, config);
  }

  public String getValidatorRestApiUrl() {
    final String url = "http://" + nodeAlias + ":" + HTTP_API_PORT;
    LOG.debug("Signer REST url: " + url);
    return url;
  }

  public ValidatorKeysApi getValidatorKeysApi() {
    return validatorKeysApi;
  }

  private String getApiPassword() {
    return "";
  }

  public void start() throws Exception {
    assertThat(started).isFalse();
    LOG.debug("Start web3signer node {}", nodeAlias);
    started = true;
    config.writeConfigFile();
    final Map<File, String> configFileMap = config.getConfigFileMap();
    this.configFiles = configFileMap.keySet();
    configFileMap.forEach(
        (localFile, targetPath) -> {
          LOG.debug("Copying {} into container at {}", localFile.getAbsoluteFile(), targetPath);
          container.withCopyFileToContainer(
              MountableFile.forHostPath(localFile.getAbsolutePath()), targetPath);
        });
    Thread.sleep(100);
    container.start();
  }

  @Override
  public void stop() {
    if (!started) {
      return;
    }
    LOG.debug("Shutting down web3signer");
    started = false;
    configFiles.forEach(
        configFile -> {
          if (!configFile.delete() && configFile.exists()) {
            throw new RuntimeException("Failed to delete config file: " + configFile);
          }
        });
    container.stop();
  }

  private URI getSignerUrl() {
    return URI.create("http://127.0.0.1:" + container.getMappedPort(HTTP_API_PORT));
  }

  public static class Config {
    private final Map<String, Object> configMap = new HashMap<>();
    private final Map<File, String> configFileMap = new HashMap<>();

    public Config() {
      configMap.put("logging", "debug");
      configMap.put("http-host-allowlist", "*");
      configMap.put("http-listen-host", "0.0.0.0");
      configMap.put("eth2.slashing-protection-enabled", false);
      configMap.put("eth2.key-manager-api-enabled", true);
    }

    public Web3SignerNode.Config withNetwork(final String network) {
      configMap.put("eth2.network", network);
      return this;
    }

    public Web3SignerNode.Config withAltairEpoch(final UInt64 altairSlot) {
      configMap.put("eth2.Xnetwork-altair-fork-epoch", altairSlot.toString());
      return this;
    }

    public Web3SignerNode.Config withBellatrixEpoch(final UInt64 bellatrixSlot) {
      configMap.put("eth2.Xnetwork-bellatrix-fork-epoch", bellatrixSlot.toString());
      return this;
    }

    public Web3SignerNode.Config withCapellaEpoch(final UInt64 capellaSlot) {
      configMap.put("eth2.Xnetwork-capella-fork-epoch", capellaSlot.toString());
      return this;
    }

    public Web3SignerNode.Config withDenebEpoch(final UInt64 denebSlot) {
      configMap.put("eth2.Xnetwork-deneb-fork-epoch", denebSlot.toString());
      return this;
    }

    public Web3SignerNode.Config withTrustedSetupFromClasspath(
        final String trustedSetupFromClasspath) {
      configMap.put("eth2.Xtrusted-setup", trustedSetupFromClasspath);
      return this;
    }

    public void writeConfigFile() throws Exception {
      final File configFile = File.createTempFile("config", ".yaml");
      configFile.deleteOnExit();
      writeConfigFileTo(configFile);
      configFileMap.put(configFile, CONFIG_FILE_PATH);
    }

    private void writeConfigFileTo(final File configFile) throws Exception {
      YAML_MAPPER.writeValue(configFile, configMap);
    }

    public Map<File, String> getConfigFileMap() {
      return configFileMap;
    }
  }
}
