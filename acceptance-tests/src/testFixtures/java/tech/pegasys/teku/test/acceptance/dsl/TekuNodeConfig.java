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

import static tech.pegasys.teku.test.acceptance.dsl.Node.CONFIG_FILE_PATH;
import static tech.pegasys.teku.test.acceptance.dsl.Node.YAML_MAPPER;

import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;

public class TekuNodeConfig {

  public static final int DEFAULT_VALIDATOR_COUNT = 64;
  public static final String INITIAL_STATE_FILE = "/state.ssz";
  private static final Logger LOG = LogManager.getLogger();

  protected String nodeType = "TekuNode";

  protected static final String TRUSTED_SETUP_FILE = "/trusted-setup.txt";
  protected final Map<String, Object> configMap;
  private final Map<File, String> configFileMap;
  protected Consumer<SpecConfigBuilder> specConfigModifier;
  protected Optional<PrivKey> maybePrivateKey;
  protected Optional<PeerId> maybePeerId;
  private final List<File> tarballsToCopy;
  private final Map<String, String> readOnlyMountPoints;
  private final Map<String, String> writableMountPoints;

  public TekuNodeConfig(
      final Map<String, Object> configMap,
      final Consumer<SpecConfigBuilder> specConfigModifier,
      final Optional<PrivKey> maybePrivateKey,
      final Optional<PeerId> maybePeerId,
      final Map<String, String> writableMountPoints,
      final Map<String, String> readOnlyMountPoints,
      final List<File> tarballsToCopy,
      final Map<File, String> configFileMap) {
    this.configMap = configMap;
    this.specConfigModifier = specConfigModifier;
    this.maybePrivateKey = maybePrivateKey;
    this.maybePeerId = maybePeerId;
    this.writableMountPoints = writableMountPoints;
    this.readOnlyMountPoints = readOnlyMountPoints;
    this.tarballsToCopy = tarballsToCopy;
    this.configFileMap = configFileMap;
  }

  public Map<String, Object> getConfigMap() {
    return configMap;
  }

  public String getNetworkName() {
    return (String) configMap.get("network");
  }

  public Consumer<SpecConfigBuilder> getSpecConfigModifier() {
    return specConfigModifier;
  }

  public String getPeerId() {
    return maybePeerId.orElseThrow().toBase58();
  }

  public String getNodeType() {
    return nodeType;
  }

  public void writeConfigFiles() throws Exception {
    if (configMap.get("sentry-config-file") != null
        && configMap.get("beacon-node-api-endpoint") != null) {
      LOG.error("Using Sentry node, removing beacon-node-api");
      configMap.remove("beacon-node-api-endpoint");
    }

    writeConfigMapToFile();
  }

  public Map<File, String> getConfigFileMap() {
    return configFileMap;
  }

  public Map<String, String> getWritableMountPoints() {
    return writableMountPoints;
  }

  public Map<String, String> getReadOnlyMountPoints() {
    return readOnlyMountPoints;
  }

  public List<File> getTarballsToCopy() {
    return tarballsToCopy;
  }

  private void writeConfigMapToFile() throws Exception {
    final File configFile = File.createTempFile("config", ".yaml");
    configFile.deleteOnExit();
    writeConfigFileTo(configFile);
    configFileMap.put(configFile, CONFIG_FILE_PATH);
  }

  void writeConfigFileTo(final File configFile) throws Exception {
    YAML_MAPPER.writeValue(configFile, configMap);
  }
}
