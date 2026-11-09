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

package tech.pegasys.teku.test.acceptance.dsl.tools;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame.OutputType;
import org.testcontainers.containers.output.WaitingConsumer;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuDockerVersion;

class TekuCLI extends GenericContainer<TekuCLI> {
  private static final Logger LOG = LogManager.getLogger();

  public TekuCLI() {
    super(TekuBeaconNode.TEKU_DOCKER_IMAGE_NAME + ":" + TekuDockerVersion.LOCAL_BUILD.getVersion());
    this.withLogConsumer(frame -> LOG.info(frame.getUtf8String().trim()));
  }

  public void waitForOutput(final String output) throws TimeoutException {
    WaitingConsumer consumer = new WaitingConsumer();
    this.followOutput(consumer, OutputType.STDOUT);
    consumer.waitUntil(frame -> frame.getUtf8String().contains(output), 30, TimeUnit.SECONDS);
  }
}
