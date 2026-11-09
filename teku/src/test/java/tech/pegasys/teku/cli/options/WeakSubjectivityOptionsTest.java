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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.cli.converter.CheckpointConverter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class WeakSubjectivityOptionsTest extends AbstractBeaconNodeCommandTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  public void weakSubjectivityCheckpoint_shouldAcceptValue() {
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();
    final String checkpointParam = checkpoint.getRoot().toHexString() + ":" + checkpoint.getEpoch();

    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--ws-checkpoint", checkpointParam);
    assertThat(config.weakSubjectivity().getWeakSubjectivityCheckpoint()).contains(checkpoint);
  }

  @Test
  public void weakSubjectivityCheckpoint_shouldDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.weakSubjectivity().getWeakSubjectivityCheckpoint()).isEmpty();
  }

  @Test
  public void weakSubjectivityCheckpoint_shouldLoadFromUrl() {
    final String checkpointParam =
        this.getClass().getResource("/" + "ws_checkpoint.json").getPath();
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--ws-checkpoint", checkpointParam);
    assertThat(config.weakSubjectivity().getWeakSubjectivityCheckpoint()).isPresent();
    final Checkpoint checkpoint =
        getResultingTekuConfiguration().weakSubjectivity().getWeakSubjectivityCheckpoint().get();

    assertThat(checkpoint.getEpoch()).isEqualTo(UInt64.valueOf(24187));
    assertThat(checkpoint.getRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x2a6b2528908d5a9ed729417740f54e7267141fd8dca1ec052fc05aa8806e56e3"));
  }

  @Test
  public void weakSubjectivityCheckpoint_handleBadValue() {
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();
    final String checkpointParam = checkpoint.getRoot().toHexString() + ":";
    final String[] args = {"--ws-checkpoint", checkpointParam};

    final int result = beaconNodeCommand.parse(args);
    String str = getCommandLineOutput();
    assertThat(str)
        .contains(
            "Invalid value for option '--ws-checkpoint': " + CheckpointConverter.CHECKPOINT_ERROR);
    assertThat(str).contains("To display full help:");
    assertThat(str).contains("--help");
    assertThat(result).isGreaterThan(0);
  }

  @Test
  public void suppressWSPeriodChecksUntilEpoch_shouldAcceptValue() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xws-suppress-errors-until-epoch", "123");
    assertThat(config.weakSubjectivity().getSuppressWSPeriodChecksUntilEpoch())
        .contains(UInt64.valueOf(123));
  }

  @Test
  public void suppressWSPeriodChecksUntilEpoch_shouldDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.weakSubjectivity().getSuppressWSPeriodChecksUntilEpoch()).isEmpty();
  }

  @Test
  public void suppressWSPeriodChecksUntilEpoch_handleBadValue() {
    final String[] args = {"--Xws-suppress-errors-until-epoch", "a:b"};
    final int result = beaconNodeCommand.parse(args);

    String str = getCommandLineOutput();
    assertThat(str).contains("Invalid value for option '--Xws-suppress-errors-until-epoch'");
    assertThat(str).contains("To display full help:");
    assertThat(str).contains("--help");
    assertThat(result).isGreaterThan(0);
  }
}
