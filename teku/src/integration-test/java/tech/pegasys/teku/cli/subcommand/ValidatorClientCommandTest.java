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

package tech.pegasys.teku.cli.subcommand;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static tech.pegasys.teku.ethereum.json.types.config.SpecConfigDataMapBuilder.GET_SPEC_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.matchers.Times;
import org.mockserver.socket.PortFactory;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.cli.BeaconNodeCommand;
import tech.pegasys.teku.cli.NodeMode;
import tech.pegasys.teku.cli.StartAction;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;

@ExtendWith(MockServerExtension.class)
public class ValidatorClientCommandTest {

  private final StringWriter stringWriter = new StringWriter();
  private final PrintWriter outputWriter = new PrintWriter(stringWriter, true);
  private final PrintWriter errorWriter = new PrintWriter(stringWriter, true);
  private final LoggingConfigurator loggingConfigurator = mock(LoggingConfigurator.class);

  private final StartAction startAction = mock(StartAction.class);

  private String[] argsNetworkOptDefault;
  private String[] argsNetworkOptAuto;
  private String[] argsNetworkOptAutoWithFailover;
  private String[] argsNetworkOptAutoInConfig;

  private ClientAndServer mockBeaconServer;
  private ClientAndServer failoverMockBeaconServer;

  private final Spec testSpec =
      SpecFactory.create(
          Resources.getResource("tech/pegasys/teku/cli/subcommand/test-spec.yaml").getPath());

  private final String networkAutoConfigFile =
      Resources.getResource("tech/pegasys/teku/cli/subcommand/networkOption_auto_config.yaml")
          .getPath();

  private final BeaconNodeCommand beaconNodeCommand =
      new BeaconNodeCommand(
          outputWriter, errorWriter, Collections.emptyMap(), startAction, loggingConfigurator);

  @BeforeEach
  public void setup(final ClientAndServer server) {
    this.mockBeaconServer = server;
    this.failoverMockBeaconServer =
        ClientAndServer.startClientAndServer(PortFactory.findFreePort());
    final String mockBeaconServerEndpoint = getMockBeaconServerEndpoint(mockBeaconServer);
    final String failoverMockBeaconServerEndpoint =
        getMockBeaconServerEndpoint(failoverMockBeaconServer);

    argsNetworkOptDefault =
        new String[] {
          "vc", "--validator-keys=keys:pass", "--beacon-node-api-endpoint", mockBeaconServerEndpoint
        };
    argsNetworkOptAuto =
        new String[] {
          "vc",
          "--network",
          "auto",
          "--validator-keys=keys:pass",
          "--beacon-node-api-endpoint",
          mockBeaconServerEndpoint
        };
    argsNetworkOptAutoWithFailover =
        new String[] {
          "vc",
          "--network",
          "auto",
          "--validator-keys=keys:pass",
          "--beacon-node-api-endpoints",
          mockBeaconServerEndpoint + "," + failoverMockBeaconServerEndpoint
        };
    argsNetworkOptAutoInConfig =
        new String[] {
          "vc",
          "--validator-keys=keys:pass",
          "--config-file",
          networkAutoConfigFile,
          "--beacon-node-api-endpoint",
          mockBeaconServerEndpoint
        };
  }

  @AfterEach
  public void tearDown() {
    mockBeaconServer.reset();
    failoverMockBeaconServer.stop();
  }

  @Test
  public void autoDetectNetwork_ShouldRetryRequest_IfFailsToFetchFromBeaconNode()
      throws JsonProcessingException {
    configureMockServer(1);
    fetchAndVerifySpec(argsNetworkOptAuto);
  }

  @Test
  public void autoDetectNetwork_ShouldFetchNetworkDetailsFromBeaconNode_IfEnabled()
      throws JsonProcessingException {
    configureMockServer(0);
    fetchAndVerifySpec(argsNetworkOptAuto);
  }

  @Test
  public void autoDetectNetwork_ShouldFetchNetworkDetailsFromFailoverNode()
      throws JsonProcessingException {
    // primary node always fails
    configureMockServer(-1);
    configureSuccessfulResponse(failoverMockBeaconServer);
    fetchAndVerifySpec(argsNetworkOptAutoWithFailover);
  }

  @Test
  public void autoDetectNetwork_ShouldRetryRequest_IfFailsToFetchFromAllConfiguredBeaconNodes()
      throws JsonProcessingException {
    configureMockServer(1);
    // failover node always fails
    configureFailedResponse(failoverMockBeaconServer, -1);
    fetchAndVerifySpec(argsNetworkOptAutoWithFailover);
  }

  @Test
  public void autoDetectNetwork_ShouldFetchNetworkDetailsFromBeaconNode_IfEnabledInConfigFile()
      throws JsonProcessingException {
    configureMockServer(0);
    fetchAndVerifySpec(argsNetworkOptAutoInConfig);
  }

  @Test
  public void networkOption_ShouldDefaultToAuto_IfNotSpecified() throws JsonProcessingException {
    configureMockServer(0);
    fetchAndVerifySpec(argsNetworkOptDefault);
  }

  private void fetchAndVerifySpec(final String[] args) {
    final int parseResult = beaconNodeCommand.parse(args);
    assertThat(parseResult).isEqualTo(0);

    final TekuConfiguration config = getResultingTekuConfiguration();
    assertThat(config.eth2NetworkConfiguration().getSpec()).isEqualTo(testSpec);
  }

  private void configureMockServer(final int fails) throws JsonProcessingException {
    configureFailedResponse(mockBeaconServer, fails);
    configureSuccessfulResponse(mockBeaconServer);
  }

  private void configureSuccessfulResponse(final ClientAndServer mockBeaconServer)
      throws JsonProcessingException {
    mockBeaconServer
        .when(request().withPath("/eth/v1/config/spec"))
        .respond(response().withStatusCode(200).withBody(getTestSpecJsonString()));
  }

  private void configureFailedResponse(final ClientAndServer mockBeaconServer, final int fails) {
    mockBeaconServer
        .when(
            request().withPath("/eth/v1/config/spec"),
            fails == -1 ? Times.unlimited() : Times.exactly(fails))
        .respond(response().withStatusCode(500));
  }

  private String getMockBeaconServerEndpoint(final ClientAndServer mockBeaconServer) {
    return String.format("http://127.0.0.1:%s/", mockBeaconServer.getLocalPort());
  }

  private String getTestSpecJsonString() throws JsonProcessingException {
    return JsonUtil.serialize(new ConfigProvider(testSpec).getConfig(), GET_SPEC_RESPONSE_TYPE);
  }

  public TekuConfiguration getResultingTekuConfiguration() {
    final ArgumentCaptor<TekuConfiguration> configCaptor =
        ArgumentCaptor.forClass(TekuConfiguration.class);
    verify(startAction).start(configCaptor.capture(), eq(NodeMode.VC_ONLY));
    return configCaptor.getValue();
  }
}
