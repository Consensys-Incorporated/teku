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

package tech.pegasys.teku;

import java.util.Optional;
import tech.pegasys.teku.config.TekuConfiguration;

/**
 * The main entry point to start Teku node
 *
 * <p>CAUTION: this API is unstable and primarily intended for debugging and testing purposes this
 * API might be changed in any version in backward incompatible way
 */
public final class TekuFacade {

  /**
   * Starts Teku node from CLI args
   *
   * @return Either {@link NodeFacade} or {@link Optional#empty()} if arguments are not supposed to
   *     start a Node (e.g. <code>--help</code> command)
   * @throws RuntimeException if invalid args supplied or an internal error while starting a Node
   */
  public static Optional<? extends NodeFacade> startFromCLIArgs(final String[] cliArgs) {
    return Teku.startFromCLIArgs(cliArgs);
  }

  public static BeaconNodeFacade startBeaconNode(final TekuConfiguration config) {
    return Teku.startBeaconNode(config);
  }

  public static ValidatorNodeFacade startValidatorNode(final TekuConfiguration config) {
    return Teku.startValidatorNode(config);
  }

  public static BootnodeFacade startBootnode(final TekuConfiguration config) {
    return Teku.startBootnode(config);
  }
}
