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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.MountableFile;
import tech.pegasys.teku.api.response.EventType;

public abstract class TekuNode extends Node {
  public static final int VALIDATOR_API_PORT = 9052;
  private static final Logger LOG = LogManager.getLogger();
  protected boolean started = false;

  private Set<File> configFiles;

  protected Optional<EventStreamListener> maybeEventStreamListener = Optional.empty();

  protected TekuNode(
      final Network network,
      final String dockerImageName,
      final TekuDockerVersion dockerImageVersion,
      final Logger log) {
    super(network, dockerImageName, dockerImageVersion, log);
  }

  public void setUpStart() throws Exception {
    assertThat(started).isFalse();
    LOG.debug("Start {} node {}", getConfig().getNodeType(), nodeAlias);
    started = true;
    getConfig().writeConfigFiles();
    final Map<File, String> configFileMap = getConfig().getConfigFileMap();
    this.configFiles = configFileMap.keySet();
    getConfig().getWritableMountPoints().forEach(this::withWritableMountPoint);
    getConfig().getReadOnlyMountPoints().forEach(this::withReadOnlyMountPoint);
    getConfig().getTarballsToCopy().forEach(this::copyContentsToWorkingDirectory);
    configFileMap.forEach(
        (localFile, targetPath) -> {
          LOG.info("Adding configuration file: {}", targetPath);
          container.withCopyFileToContainer(
              MountableFile.forHostPath(localFile.getAbsolutePath()), targetPath);
        });
  }

  public abstract TekuNodeConfig getConfig();

  public void start() throws Exception {
    setUpStart();
    container.start();
  }

  // be aware that these regex's slow down quickly the bigger they are
  // by default 10 second timeout, needs to be a short, simple match.
  public void startWithFailure(final String expectedError) throws Exception {
    startWithFailure(expectedError, 10);
  }

  public void startWithFailure(final String expectedError, final int timeout) throws Exception {
    setUpStart();
    container.waitingFor(
        new LogMessageWaitStrategy()
            .withRegEx(".*?" + expectedError + ".*")
            .withStartupTimeout(Duration.ofSeconds(timeout)));
    container.start();
  }

  public void stop(final boolean cleanup) {
    if (!started) {
      return;
    }
    LOG.debug("Shutting down");
    started = false;
    maybeEventStreamListener.ifPresent(EventStreamListener::close);
    if (cleanup) {
      configFiles.forEach(
          configFile -> {
            if (!configFile.delete() && configFile.exists()) {
              throw new RuntimeException("Failed to delete config file: " + configFile);
            }
          });
    }
    container.stop();
  }

  @Override
  public void stop() {
    stop(true);
  }

  public void startEventListener(final EventType... eventTypes) {
    maybeEventStreamListener =
        Optional.of(new EventStreamListener(getEventUrl(List.of(eventTypes))));
    waitFor(() -> assertThat(maybeEventStreamListener.get().isReady()).isTrue());
  }

  /**
   * Copies data directory from node into a temporary directory.
   *
   * @return A file containing the data directory.
   */
  public File getDataDirectoryFromContainer() throws Exception {
    File dbTar = File.createTempFile("database", ".tar");
    dbTar.deleteOnExit();
    copyDirectoryToTar(DATA_PATH, dbTar);
    return dbTar;
  }

  public File stopAndGetDataDirectoryFromContainer() throws Exception {
    assertThat(started).isTrue();
    container.getDockerClient().stopContainerCmd(container.getContainerId()).exec();
    final File dataDirectory = getDataDirectoryFromContainer();
    stop();
    return dataDirectory;
  }

  public void waitForOwnedValidatorCount(final int expectedValidatorCount) {
    LOG.debug("Waiting for validator count to be {}", expectedValidatorCount);
    waitFor(
        () -> {
          final double validatorCount = getMetricValue("validator_local_validator_count");
          LOG.debug("Current validator count {}", validatorCount);
          assertThat(validatorCount).isEqualTo(expectedValidatorCount);
        });
  }

  private String getEventUrl(final List<EventType> events) {
    final String eventTypes =
        events.isEmpty()
            ? ""
            : "?topics=" + events.stream().map(EventType::name).collect(Collectors.joining(","));
    LOG.debug("Event Types: {}", eventTypes);
    return getRestApiUrl() + "/eth/v1/events" + eventTypes;
  }

  protected URI getRestApiUrl() {
    return URI.create("http://127.0.0.1:" + container.getMappedPort(REST_API_PORT));
  }

  public URI getValidatorApiUrl() {
    final boolean isUseSsl =
        (boolean) getConfig().getConfigMap().getOrDefault("Xvalidator-api-ssl-enabled", true);
    final String prefix = isUseSsl ? "https" : "http";

    final URI uri =
        URI.create(prefix + "://127.0.0.1:" + container.getMappedPort(VALIDATOR_API_PORT));
    LOG.debug("Validator URL: {}", uri);
    return uri;
  }
}
