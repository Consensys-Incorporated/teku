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

import static tech.pegasys.teku.config.BeaconNodeConfig.DEFAULT_EVENT_CHANNEL_VIRTUAL_THREADS_ENABLED;

import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;

public class BeaconNodeOptions {

  @Option(
      names = {"--Xevent-channel-virtual-threads-enabled"},
      description = "Use virtual threads for event channel delivery",
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true,
      fallbackValue = "true",
      arity = "0..1")
  private boolean eventChannelVirtualThreadsEnabled = DEFAULT_EVENT_CHANNEL_VIRTUAL_THREADS_ENABLED;

  public void configure(final TekuConfiguration.Builder builder) {
    builder.beaconNode(b -> b.eventChannelVirtualThreadsEnabled(eventChannelVirtualThreadsEnabled));
  }
}
