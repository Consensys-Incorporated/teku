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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;

public class TransitionCommandTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldProcessBlocksInSlotNumberOrder() throws IOException, URISyntaxException {
    final String[] argsNetworkOptOnParent =
        new String[] {
          "transition",
          "blocks",
          "--network=mainnet",
          String.format("-i=%s", getPath("transition/state-from-slot-7.ssz")),
          getPath("transition/block-10.ssz"),
          getPath("transition/block-8.ssz"),
          getPath("transition/block-9.ssz"),
        };
    ByteArrayOutputStream resultOutput = getStdOut();
    int parseResult = beaconNodeCommand.parse(argsNetworkOptOnParent);
    assertThat(parseResult).isEqualTo(0);
    assertThat(resultOutput.toByteArray())
        .isEqualTo(
            Resources.toByteArray(
                Resources.getResource(
                    TransitionCommandTest.class, "transition/state-from-slot-10.ssz")));
  }

  @Test
  public void shouldDeserializeAsHexWhenSszFails() throws IOException, URISyntaxException {
    final String[] argsNetworkOptOnParent =
        new String[] {
          "transition",
          "blocks",
          "--network=mainnet",
          String.format("-i=%s", getPath("transition/state-from-slot-7.ssz")),
          getPath("transition/block-10.ssz"),
          getPath("transition/block-8.hex"),
          getPath("transition/block-9.ssz")
        };
    ByteArrayOutputStream resultOutput = getStdOut();
    int parseResult = beaconNodeCommand.parse(argsNetworkOptOnParent);
    assertThat(parseResult).isEqualTo(0);
    assertThat(resultOutput.toByteArray())
        .isEqualTo(
            Resources.toByteArray(
                Resources.getResource(
                    TransitionCommandTest.class, "transition/state-from-slot-10.ssz")));
  }

  /** Safe cross-platform implementation */
  private String getPath(final String filename) throws URISyntaxException {
    return Paths.get(Resources.getResource(TransitionCommandTest.class, filename).toURI())
        .toFile()
        .getPath();
  }
}
