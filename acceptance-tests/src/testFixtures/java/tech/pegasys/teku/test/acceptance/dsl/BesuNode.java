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

package tech.pegasys.teku.test.acceptance.dsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.interop.MergedGenesisTestBuilder;
import tech.pegasys.teku.test.acceptance.dsl.executionrequests.ExecutionRequestsService;

public class BesuNode extends Node {

  private static final Logger LOG = LogManager.getLogger();
  private static final int JSON_RPC_PORT = 8545;
  private static final int ENGINE_JSON_RPC_PORT = 8550;
  private static final int P2P_PORT = 30303;
  private static final String BESU_DOCKER_IMAGE_NAME = "hyperledger/besu";
  private static final String BESU_CONFIG_FILE_PATH = "/config.toml";
  private static final ObjectMapper TOML_MAPPER = new TomlMapper();

  private final Config config;

  private BesuNode(
      final Network network,
      final BesuDockerVersion version,
      final Config config,
      final Map<String, String> genesisOverrides) {
    super(network, BESU_DOCKER_IMAGE_NAME + ":" + version.getVersion(), LOG);
    this.config = config;

    container
        .withExposedPorts(JSON_RPC_PORT, ENGINE_JSON_RPC_PORT, P2P_PORT)
        .waitingFor(new HttpWaitStrategy().forPort(JSON_RPC_PORT).forPath("/liveness"));

    final List<String> startCommands = new ArrayList<>();
    startCommands.add("--config-file");
    startCommands.add(BESU_CONFIG_FILE_PATH);

    if (!genesisOverrides.isEmpty()) {
      final String overrides =
          genesisOverrides.entrySet().stream()
              .map((e) -> e.getKey() + "=" + e.getValue())
              .collect(Collectors.joining(","));

      startCommands.add("--override-genesis-config");
      startCommands.add(overrides);
    }

    container.withCommand(startCommands.toArray(String[]::new));
  }

  public static BesuNode create(
      final Network network,
      final BesuDockerVersion version,
      final Consumer<Config> configOptions,
      final Map<String, String> genesisOverrides) {

    final Config config = new Config();
    configOptions.accept(config);

    return new BesuNode(network, version, config, genesisOverrides);
  }

  public void start() throws Exception {
    LOG.debug("Start node {}", nodeAlias);

    final Map<File, String> configFiles = config.write();
    configFiles.forEach(
        (localFile, targetPath) ->
            container.withCopyFileToContainer(
                MountableFile.forHostPath(localFile.getAbsolutePath()), targetPath));

    container.withCopyFileToContainer(
        MountableFile.forClasspathResource(config.genesisFilePath), "/genesis.json");

    container.start();
  }

  public void restartWithEmptyDatabase() throws Exception {
    container.execInContainer("rm", "-rf", "/opt/besu");
    container.stop();
    container.start();
  }

  public Eth1Address getDepositContractAddress() {
    return Eth1Address.fromHexString("0x00000961ef480eb55e80d19ad83579a64c007002");
  }

  /*
   Defined on https://eips.ethereum.org/EIPS/eip-7002 (WITHDRAWAL_REQUEST_PREDEPLOY_ADDRESS)
  */
  public Eth1Address getWithdrawalRequestContractAddress() {
    return Eth1Address.fromHexString("0x00000961Ef480Eb55e80D19ad83579A64c007002");
  }

  /*
   Defined on https://eips.ethereum.org/EIPS/eip-7251 (CONSOLIDATION_REQUEST_PREDEPLOY_ADDRESS)
  */
  public Eth1Address getConsolidationRequestContractAddress() {
    return Eth1Address.fromHexString("0x0000BBdDc7CE488642fb579F8B00f3a590007251");
  }

  public String getInternalJsonRpcUrl() {
    return "http://" + nodeAlias + ":" + JSON_RPC_PORT;
  }

  public String getExternalJsonRpcUrl() {
    return "http://127.0.0.1:" + container.getMappedPort(JSON_RPC_PORT);
  }

  public String getInternalEngineJsonRpcUrl() {
    return "http://" + nodeAlias + ":" + ENGINE_JSON_RPC_PORT;
  }

  public String getInternalEngineWebsocketsRpcUrl() {
    return "ws://" + nodeAlias + ":" + ENGINE_JSON_RPC_PORT;
  }

  private String getInternalP2pUrl(final String nodeId) {
    return "enode://" + nodeId + "@" + getInternalIpAddress() + ":" + P2P_PORT;
  }

  private String getInternalIpAddress() {
    return container.getContainerInfo().getNetworkSettings().getNetworks().values().stream()
        .findFirst()
        .get()
        .getIpAddress();
  }

  private String fetchEnodeUrl() throws Exception {
    final URI baseUri = new URI(getExternalJsonRpcUrl());
    final String response =
        httpClient.post(
            baseUri, "", OBJECT_MAPPER.writeValueAsString(new Request("admin_nodeInfo")));
    return getInternalP2pUrl(OBJECT_MAPPER.readTree(response).get("result").get("id").asText());
  }

  public Boolean addPeer(final BesuNode node) throws Exception {
    final String enode = node.fetchEnodeUrl();
    final URI baseUri = new URI(getExternalJsonRpcUrl());
    final String response =
        httpClient.post(
            baseUri, "", OBJECT_MAPPER.writeValueAsString(new Request("admin_addPeer", enode)));
    return OBJECT_MAPPER.readTree(response).get("result").asBoolean();
  }

  /**
   * Returns the {@code result} of {@code eth_getBlockByNumber(blockLabel, false)}, e.g. the block
   * the execution layer currently treats as {@code "safe"} or {@code "finalized"} (as set by the
   * consensus layer via {@code engine_forkchoiceUpdated}). Fields such as {@code hash} and {@code
   * number} are hex strings.
   */
  public JsonNode getExecutionBlock(final String blockLabel) throws Exception {
    final URI baseUri = new URI(getExternalJsonRpcUrl());
    final String response =
        httpClient.post(
            baseUri,
            "",
            OBJECT_MAPPER.writeValueAsString(
                new Request("eth_getBlockByNumber", blockLabel, false)));
    final JsonNode responseNode = OBJECT_MAPPER.readTree(response);
    final JsonNode error = responseNode.get("error");
    if (error != null && !error.isNull()) {
      throw new IllegalStateException(
          "eth_getBlockByNumber(" + blockLabel + ") failed with error: " + error);
    }
    return responseNode.get("result");
  }

  public String getRichBenefactorAddress() {
    return "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73";
  }

  public String getRichBenefactorKey() {
    return "0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";
  }

  public ExecutionPayloadHeader createGenesisExecutionPayload(final Spec spec) {
    try {
      final String genesisConfig =
          Resources.toString(Resources.getResource(config.genesisFilePath), StandardCharsets.UTF_8);
      return MergedGenesisTestBuilder.createPayloadForBesuGenesis(
          spec.getGenesisSchemaDefinitions(), genesisConfig);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Sends a transaction to the withdrawal request contract in the execution layer to create a
   * withdrawal request.
   *
   * @param eth1PrivateKey the private key of the eth1 account that will sign the transaction to the
   *     withdrawal contract (has to match the validator withdrawal credentials)
   * @param publicKey validator public key
   * @param amountInGwei the amount for the withdrawal request (zero for full withdrawal, greater
   *     than zero for partial withdrawal)
   */
  public void createWithdrawalRequest(
      final String eth1PrivateKey, final BLSPublicKey publicKey, final UInt64 amountInGwei)
      throws Exception {
    final Credentials eth1Credentials = Credentials.create(eth1PrivateKey);
    try (final ExecutionRequestsService executionRequestsService =
        new ExecutionRequestsService(
            getExternalJsonRpcUrl(),
            eth1Credentials,
            getWithdrawalRequestContractAddress(),
            getConsolidationRequestContractAddress())) {

      final SafeFuture<TransactionReceipt> future =
          executionRequestsService.createWithdrawalRequest(publicKey, amountInGwei);
      Waiter.waitFor(future, Duration.ofMinutes(1));
    }
  }

  /**
   * Sends a transaction to the consolidation request contract in the execution layer to create a
   * consolidation request.
   *
   * @param eth1PrivateKey the private key of the eth1 account that will sign the transaction to the
   *     consolidation contract (has to match the source validator withdrawal credentials)
   * @param sourceValidatorPublicKey source validator public key
   * @param targetValidatorPublicKey target validator public key
   */
  public void createConsolidationRequest(
      final String eth1PrivateKey,
      final BLSPublicKey sourceValidatorPublicKey,
      final BLSPublicKey targetValidatorPublicKey)
      throws Exception {
    final Credentials eth1Credentials = Credentials.create(eth1PrivateKey);
    try (final ExecutionRequestsService executionRequestsService =
        new ExecutionRequestsService(
            getExternalJsonRpcUrl(),
            eth1Credentials,
            getWithdrawalRequestContractAddress(),
            getConsolidationRequestContractAddress())) {

      final SafeFuture<TransactionReceipt> future =
          executionRequestsService.createConsolidationRequest(
              sourceValidatorPublicKey, targetValidatorPublicKey);
      Waiter.waitFor(future, Duration.ofMinutes(1));
    }
  }

  @SuppressWarnings("unused")
  private static class Request {

    public final String jsonrpc = "2.0";
    public final String method;
    public final Object[] params;
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);
    public final int id;

    public Request(final String method, final Object... params) {
      this.method = method;
      this.params = params;
      this.id = ID_COUNTER.incrementAndGet();
    }
  }

  public static class Config {

    private static final String[] MERGE_RPC_MODULES = new String[] {"ETH,NET,WEB3,ENGINE,ADMIN"};
    private final Map<String, Object> configMap = new HashMap<>();
    private Optional<URL> maybeJwtFile = Optional.empty();
    private String genesisFilePath = "besu/depositContractGenesis.json";

    public Config() {
      configMap.put("rpc-http-enabled", true);
      configMap.put("rpc-ws-enabled", true);
      configMap.put("rpc-http-port", Integer.toString(JSON_RPC_PORT));
      configMap.put("rpc-http-cors-origins", new String[] {"*"});
      configMap.put("host-allowlist", new String[] {"*"});
      configMap.put("discovery-enabled", false);
      configMap.put("genesis-file", "/genesis.json");
    }

    public BesuNode.Config withGenesisFile(final String genesisFilePath) {
      this.genesisFilePath = genesisFilePath;
      return this;
    }

    public BesuNode.Config withMergeSupport() {
      configMap.put("rpc-http-api", MERGE_RPC_MODULES);
      configMap.put("rpc-ws-api", MERGE_RPC_MODULES);
      configMap.put("engine-rpc-port", Integer.toString(ENGINE_JSON_RPC_PORT));
      configMap.put("engine-host-allowlist", new String[] {"*"});
      return this;
    }

    public BesuNode.Config withJwtTokenAuthorization(final URL jwtFile) {
      configMap.put("engine-jwt-disabled", false);
      configMap.put("engine-jwt-secret", JWT_SECRET_FILE_PATH);
      this.maybeJwtFile = Optional.of(jwtFile);
      return this;
    }

    public BesuNode.Config withP2pEnabled(final boolean enabled) {
      configMap.put("p2p-enabled", enabled);
      return this;
    }

    public Map<File, String> write() throws Exception {
      final Map<File, String> configFiles = new HashMap<>();

      final File configFile = File.createTempFile("config", ".toml");
      configFile.deleteOnExit();
      writeTo(configFile);
      configFiles.put(configFile, BESU_CONFIG_FILE_PATH);

      if (maybeJwtFile.isPresent()) {
        configFiles.put(copyToTmpFile(maybeJwtFile.get()), JWT_SECRET_FILE_PATH);
      }

      return configFiles;
    }

    private void writeTo(final File configFile) throws Exception {
      TOML_MAPPER.writeValue(configFile, configMap);
    }
  }
}
