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

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withAnyLabels;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withNameEqualsTo;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withValueEqualTo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.MetricLabelsCondition;
import tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.MetricNameCondition;
import tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.MetricValuesCondition;
import tech.pegasys.teku.test.acceptance.dsl.metrics.MetricFetcher;
import tech.pegasys.teku.test.acceptance.dsl.metrics.MetricMatcher;
import tech.pegasys.teku.test.acceptance.dsl.metrics.MetricValue;

public abstract class Node {

  private static final Logger LOG = LogManager.getLogger();
  public static final String TEKU_DOCKER_IMAGE_NAME = "consensys/teku";
  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  protected final SimpleHttpClient httpClient = new SimpleHttpClient();
  protected static final int REST_API_PORT = 9051;
  protected static final int METRICS_PORT = 8008;
  protected static final String CONFIG_FILE_PATH = "/config.yaml";
  protected static final String NETWORK_FILE_PATH = "/network.yaml";
  protected static final String PRIVATE_KEY_FILE_PATH = "/private-key.txt";
  public static final String JWT_SECRET_FILE_PATH = "/jwt-secret.hex";
  protected static final String SENTRY_NODE_CONFIG_FILE_PATH = "/sentry-node-config.json";
  protected static final String WORKING_DIRECTORY = "/opt/teku/";
  protected static final String DATA_PATH = WORKING_DIRECTORY + "data/";
  protected static final int P2P_PORT = 9000;
  protected static final ObjectMapper YAML_MAPPER =
      new ObjectMapper(new YAMLFactory().disable(WRITE_DOC_START_MARKER));
  protected final NodeContainer container;
  protected final String nodeAlias;

  private static final AtomicInteger NODE_UNIQUIFIER = new AtomicInteger();

  protected Node(
      final Network network,
      final String dockerImageName,
      final TekuDockerVersion dockerImageVersion,
      final Logger log) {
    this(network, dockerImageName + ":" + dockerImageVersion.getVersion(), log);
  }

  protected Node(final Network network, final String dockerImage, final Logger log) {
    this.nodeAlias =
        getClass().getSimpleName().toLowerCase(Locale.US) + NODE_UNIQUIFIER.incrementAndGet();
    this.container =
        new NodeContainer(dockerImage)
            .withImagePullPolicy(PullPolicy.defaultPolicy())
            .withNetwork(network)
            .withNetworkAliases(nodeAlias)
            .withLogConsumer(frame -> log.debug(frame.getUtf8String().trim()));
  }

  protected Node(final Network network, final ImageFromDockerfile image, final Logger log) {
    this.nodeAlias =
        getClass().getSimpleName().toLowerCase(Locale.US) + NODE_UNIQUIFIER.incrementAndGet();
    this.container =
        new NodeContainer(image)
            .withNetwork(network)
            .withNetworkAliases(nodeAlias)
            .withLogConsumer(frame -> log.debug(frame.getUtf8String().trim()));
  }

  public void stop() {
    container.stop();
  }

  public void waitForExit() {
    Waiter.waitFor(
        () -> assertThat(container.isRunning()).describedAs("Container is running").isFalse());
  }

  public void waitForExit(final int timeoutInSeconds) {
    Waiter.waitFor(
        () -> assertThat(container.isRunning()).describedAs("Container is running").isFalse(),
        timeoutInSeconds,
        TimeUnit.SECONDS);
  }

  public int waitForEpochAtOrAbove(final int epoch) {
    return waitForEpochAtOrAbove(epoch, Duration.ofMinutes(2));
  }

  public int waitForEpochAtOrAbove(final int epoch, final Duration timeout) {
    final AtomicInteger actualEpoch = new AtomicInteger();
    waitFor(
        () -> {
          final int currentEpoch = getCurrentEpoch();
          assertThat(currentEpoch).isGreaterThanOrEqualTo(epoch);
          actualEpoch.set(currentEpoch);
        },
        (int) timeout.toSeconds(),
        TimeUnit.SECONDS);
    return actualEpoch.get();
  }

  protected int getCurrentEpoch() {
    return (int) getMetricValue("beacon_epoch");
  }

  public void waitForNextEpoch() {
    final int currentEpoch = waitForEpochAtOrAbove(0);
    waitForEpochAtOrAbove(currentEpoch + 1);
  }

  public double getMetricValue(final String metricName) {
    final MetricFetcher metricFetcher = new MetricFetcher(httpClient, getMetricsUrl());
    final MetricValue metricValue = metricFetcher.getSingleMetricByName(metricName);

    LOG.debug("Metric {}: {}", metricName, metricValue.getValue());
    return metricValue.getValue();
  }

  public void waitForLogMessageContaining(final String filter) {
    waitFor(
        () -> assertThat(getFilteredOutputContaining(filter)).isNotEmpty(), 2, TimeUnit.MINUTES);
  }

  public void waitForLogMessageEndingWith(final String filter) {
    waitFor(
        () -> assertThat(getFilteredOutputEndingWith(filter)).isNotEmpty(), 2, TimeUnit.MINUTES);
  }

  public void waitForAllInAnyOrder(final Runnable... runnables) throws InterruptedException {
    try (final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      final List<Runnable> runnableList = List.of(runnables);
      LOG.debug("Waiting for {} runnables {}", runnableList.size(), runnableList);
      final CountDownLatch latch = new CountDownLatch(runnableList.size());
      for (int i = 0; i < runnableList.size(); ++i) {
        final int currentIndex = i;
        executor.execute(
            () -> {
              final Runnable runnable = runnableList.get(currentIndex);
              runnable.run();
              latch.countDown();
              LOG.debug("Runnable #{} completed: {}", currentIndex, runnable);
            });
      }
      assertTrue(
          latch.await(2, TimeUnit.MINUTES),
          () ->
              String.format(
                  "%d/%d runnables did not complete", latch.getCount(), runnableList.size()));
    }
  }

  protected void waitForMetricWithValue(final String metricName, final double value) {
    waitForMetric(withNameEqualsTo(metricName), withAnyLabels(), withValueEqualTo(value));
  }

  protected void waitForMetric(
      final MetricNameCondition nameCondition,
      final MetricLabelsCondition labelsCondition,
      final MetricValuesCondition valueCondition) {
    waitForMetric(nameCondition, labelsCondition, valueCondition, 5, TimeUnit.MINUTES);
  }

  protected void waitForMetric(
      final MetricNameCondition nameCondition,
      final MetricLabelsCondition labelsCondition,
      final MetricValuesCondition valueCondition,
      final int timeoutAmount,
      final TimeUnit timeoutUnit) {
    final MetricFetcher metricFetcher = new MetricFetcher(httpClient, getMetricsUrl());

    waitFor(
        () -> {
          final List<MetricValue> metrics = metricFetcher.getAllMetrics();
          assertThat(
                  MetricMatcher.anyMatching(
                      metrics, nameCondition, labelsCondition, valueCondition))
              .withFailMessage("No matching metric")
              .isPresent();
        },
        timeoutAmount,
        timeoutUnit);
  }

  protected void waitFor(
      final Waiter.Condition condition, final int timeoutAmount, final TimeUnit timeoutUnit) {
    try {
      Waiter.waitFor(condition, timeoutAmount, timeoutUnit);
    } catch (final Throwable t) {
      fail(t.getMessage() + " Logs: " + container.getLogs(), t);
    }
  }

  protected void waitFor(final Waiter.Condition condition) {
    try {
      Waiter.waitFor(condition, 1, TimeUnit.MINUTES);
    } catch (final Throwable t) {
      fail(t.getMessage() + " Logs: " + container.getLogs(), t);
    }
  }

  private URI getMetricsUrl() {
    return URI.create("http://127.0.0.1:" + container.getMappedPort(METRICS_PORT));
  }

  public String getLoggedErrors() {
    return container.getLogs(OutputFrame.OutputType.STDERR);
  }

  public List<String> getFilteredOutputContaining(final String filter) {
    return container
        .getLogs(OutputFrame.OutputType.STDOUT)
        .lines()
        .filter(s -> s.contains(filter))
        .collect(Collectors.toList());
  }

  public List<String> getFilteredOutputEndingWith(final String filter) {
    return container
        .getLogs(OutputFrame.OutputType.STDOUT)
        .lines()
        .filter(s -> s.endsWith(filter))
        .collect(Collectors.toList());
  }

  public void captureDebugArtifacts(final File artifactDir) {}

  protected void copyDirectoryToTar(final String sourcePath, final File localTargetDir) {
    try {
      try (InputStream inputStream =
          container
              .getDockerClient()
              .copyArchiveFromContainerCmd(container.getContainerId(), sourcePath)
              .exec()) {
        IOUtils.copy(inputStream, Files.newOutputStream(localTargetDir.toPath()));
      }
    } catch (final IOException e) {
      throw new RuntimeException("Failed to copy directory from " + nodeAlias, e);
    }
  }

  protected static File copyToTmpFile(final URL fileUrl) throws Exception {
    return copyToTmpFile(fileUrl, ".tmp");
  }

  protected static File copyToTmpFile(final URL fileUrl, final String suffix) throws Exception {
    final File tmpFile = File.createTempFile("teku", suffix);
    tmpFile.deleteOnExit();
    try (InputStream inputStream = fileUrl.openStream();
        FileOutputStream out = new FileOutputStream(tmpFile)) {
      org.testcontainers.shaded.org.apache.commons.io.IOUtils.copy(inputStream, out);
    } catch (Exception ex) {
      LOG.error("Failed to copy provided URL to temporary file", ex);
    }
    return tmpFile;
  }

  /** Copies contents of the given directory into node's working directory. */
  public void copyContentsToWorkingDirectory(final File tarFile) {
    container.withExpandedTarballToContainer(tarFile, WORKING_DIRECTORY);
  }

  public void withWritableMountPoint(final String filePath, final String nodePath) {
    container.withFileSystemBind(filePath, nodePath, BindMode.READ_WRITE);
  }

  public void withReadOnlyMountPoint(final String filePath, final String nodePath) {
    container.withFileSystemBind(filePath, nodePath, BindMode.READ_ONLY);
  }
}
