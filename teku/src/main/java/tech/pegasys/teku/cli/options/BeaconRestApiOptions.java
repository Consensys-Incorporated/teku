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

package tech.pegasys.teku.cli.options;

import java.util.List;
import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.config.TekuConfiguration;

public class BeaconRestApiOptions {

  @CommandLine.Spec CommandLine.Model.CommandSpec cliSpec;

  private int maxUrlLength = BeaconRestApiConfig.DEFAULT_MAX_URL_LENGTH;

  @Option(
      names = {"--Xrest-api-async-timeout-seconds"},
      paramLabel = "<INTEGER>",
      description = "Timeout for asynchronous Beacon REST API requests in seconds",
      arity = "1",
      hidden = true)
  private long restApiAsyncTimeoutSeconds =
      BeaconRestApiConfig.DEFAULT_REST_API_ASYNC_TIMEOUT.toSeconds();

  @Option(
      names = {"--rest-api-port"},
      paramLabel = "<INTEGER>",
      description = "Port number of Beacon Rest API",
      arity = "1")
  private Integer restApiPort;

  @Option(
      names = {"--rest-api-docs-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enable swagger-docs and swagger-ui endpoints",
      fallbackValue = "true",
      arity = "0..1")
  private boolean restApiDocsEnabled = false;

  @Option(
      names = {"--rest-api-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables Beacon Rest API",
      fallbackValue = "true",
      arity = "0..1")
  private Boolean restApiEnabled;

  @Option(
      names = {"--rest-api-interface"},
      paramLabel = "<NETWORK>",
      description = "Interface of Beacon Rest API",
      arity = "1")
  private String restApiInterface = BeaconRestApiConfig.DEFAULT_REST_API_INTERFACE;

  @Option(
      names = {"--rest-api-host-allowlist"},
      paramLabel = "<hostname>",
      description = "Comma-separated list of hostnames to allow, or * to allow any host",
      split = ",",
      arity = "0..*")
  private final List<String> restApiHostAllowlist =
      BeaconRestApiConfig.DEFAULT_REST_API_HOST_ALLOWLIST;

  @Option(
      names = {"--rest-api-cors-origins"},
      paramLabel = "<origin>",
      description = "Comma separated list of origins to allow, or * to allow any origin",
      split = ",",
      arity = "0..*")
  private final List<String> restApiCorsAllowedOrigins =
      BeaconRestApiConfig.DEFAULT_REST_API_CORS_ALLOWED_ORIGINS;

  @Option(
      names = {"--Xrest-api-light-client-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Enables Beacon Rest API light client endpoints and storage of light client data.",
      fallbackValue = "true",
      arity = "0..1",
      hidden = true)
  private boolean restApiLightClientEnabled = false;

  @Option(
      names = {"--Xrest-api-max-pending-events"},
      paramLabel = "<INTEGER>",
      hidden = true)
  private int maxPendingEvents = BeaconRestApiConfig.DEFAULT_MAX_EVENT_QUEUE_SIZE;

  @Option(
      names = {"--Xrest-api-max-url-length"},
      description = "Set the maximum url length for rest api requests",
      paramLabel = "<INTEGER>",
      defaultValue = "65535",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  public void setMaxUrlLength(final int maxUrlLength) {
    if (maxUrlLength < 4096 || maxUrlLength > 1052672) {
      throw new CommandLine.ParameterException(
          cliSpec.commandLine(),
          String.format(
              "Invalid value '%s' for option '--Xrest-api-max-url-length': "
                  + "value outside of the expected range (min: 4096, max: 1052672)",
              maxUrlLength));
    }
    this.maxUrlLength = maxUrlLength;
  }

  @Option(
      names = {"--beacon-liveness-tracking-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Track validator liveness and enable requests to the liveness rest api.",
      arity = "0..1",
      fallbackValue = "true")
  private Boolean beaconLivenessTrackingEnabled =
      BeaconRestApiConfig.DEFAULT_BEACON_LIVENESS_TRACKING_ENABLED;

  @Option(
      names = {"--Xrest-api-validator-threads"},
      description = "Set the number of threads used to handle validator api requests",
      paramLabel = "<INTEGER>",
      hidden = true)
  private Integer validatorThreads;

  @Option(
      names = {"--Xrest-api-virtual-threads-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables virtual threads for REST API request handling",
      fallbackValue = "true",
      arity = "0..1",
      hidden = true)
  private boolean restApiVirtualThreadsEnabled =
      BeaconRestApiConfig.DEFAULT_REST_API_VIRTUAL_THREADS_ENABLED;

  @Option(
      names = {"--Xrest-api-virtual-threads-max-concurrent-tasks"},
      paramLabel = "<INTEGER>",
      hidden = true)
  private int restApiVirtualThreadsMaxConcurrentTasks =
      BeaconRestApiConfig.DEFAULT_REST_API_VIRTUAL_THREADS_MAX_CONCURRENT_TASKS;

  @Option(
      names = {"--rest-api-getblobs-sidecars-download-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Enables Get Blobs API ability to reconstruct blobs via on-demand p2p columns downloading",
      arity = "0..1",
      fallbackValue = "true")
  private Boolean getBlobsSidecarsDownloadEnabled =
      BeaconRestApiConfig.DEFAULT_GETBLOBS_SIDECARS_DOWNLOAD_ENABLED;

  @Option(
      names = {"--rest-api-getblobs-sidecars-download-timeout"},
      paramLabel = "<INTEGER>",
      description = "Sidecars download timeout in seconds",
      arity = "1")
  private long getBlobsSidecarsDownloadTimeoutSeconds =
      BeaconRestApiConfig.DEFAULT_GETBLOBS_SIDECARS_DOWNLOAD_TIMEOUT.toSeconds();

  public void configure(final TekuConfiguration.Builder builder) {
    // Set defaults
    if (restApiEnabled == null && restApiPort == null) {
      restApiEnabled = false;
      restApiPort = BeaconRestApiConfig.DEFAULT_REST_API_PORT;
    } else if (restApiEnabled == null) {
      restApiEnabled = true;
    } else if (restApiPort == null) {
      restApiPort = BeaconRestApiConfig.DEFAULT_REST_API_PORT;
    }

    builder.restApi(
        restApiBuilder ->
            restApiBuilder
                .restApiEnabled(restApiEnabled)
                .restApiLightClientEnabled(restApiLightClientEnabled)
                .restApiDocsEnabled(restApiDocsEnabled)
                .restApiPort(restApiPort)
                .restApiInterface(restApiInterface)
                .restApiAsyncTimeoutSeconds(restApiAsyncTimeoutSeconds)
                .restApiHostAllowlist(restApiHostAllowlist)
                .restApiCorsAllowedOrigins(restApiCorsAllowedOrigins)
                .maxUrlLength(maxUrlLength)
                .beaconLivenessTrackingEnabled(beaconLivenessTrackingEnabled)
                .getBlobsSidecarsDownloadEnabled(getBlobsSidecarsDownloadEnabled)
                .getBlobsSidecarsDownloadTimeoutSeconds(getBlobsSidecarsDownloadTimeoutSeconds)
                .maxPendingEvents(maxPendingEvents)
                .validatorThreads(Optional.ofNullable(validatorThreads))
                .restApiVirtualThreadsEnabled(restApiVirtualThreadsEnabled)
                .restApiVirtualThreadsMaxConcurrentTasks(restApiVirtualThreadsMaxConcurrentTasks));
  }
}
