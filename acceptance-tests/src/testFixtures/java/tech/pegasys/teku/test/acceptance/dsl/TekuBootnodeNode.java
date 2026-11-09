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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;

public class TekuBootnodeNode extends TekuNode {

  private static final Logger LOG = LogManager.getLogger();

  private final TekuNodeConfig config;

  private TekuBootnodeNode(
      final Network network, final TekuDockerVersion version, final TekuNodeConfig config) {
    super(network, TEKU_DOCKER_IMAGE_NAME, version, LOG);
    this.config = config;

    container
        .withWorkingDirectory(WORKING_DIRECTORY)
        .withCommand("bootnode", "--config-file", CONFIG_FILE_PATH);
  }

  public static TekuBootnodeNode create(
      final Network network, final TekuDockerVersion version, final TekuNodeConfig tekuNodeConfig) {
    return new TekuBootnodeNode(network, version, tekuNodeConfig);
  }

  @Override
  public TekuNodeConfig getConfig() {
    return config;
  }

  public void waitForDiscoveryStarted() {
    waitForLogMessageContaining("Bootnode service started");
  }

  public void waitForNodeENRContaining(final String enrSubstring) {
    waitForLogMessageContaining("Bootnode ENR = ");
    waitForLogMessageContaining(enrSubstring);
  }
}
