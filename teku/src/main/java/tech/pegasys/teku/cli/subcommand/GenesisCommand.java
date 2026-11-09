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

import static tech.pegasys.teku.infrastructure.logging.SubCommandLogger.SUB_COMMAND_LOG;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.MinimalEth2NetworkOptions;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

@Command(
    name = "genesis",
    description = "Commands for generating genesis state",
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class GenesisCommand {

  @Command(
      name = "mock",
      description = "Generate a mock genesis state",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public void generate(
      @Mixin final MockGenesisParams genesisParams,
      @Mixin final MinimalEth2NetworkOptions networkOptions)
      throws IOException {
    // Output to stdout if no file is specified
    final Spec spec = networkOptions.getSpec();
    final boolean outputToFile =
        genesisParams.outputFile != null && !genesisParams.outputFile.isBlank();
    final OutputStream fileStream =
        outputToFile ? new FileOutputStream(genesisParams.outputFile) : System.out;
    try {
      if (outputToFile) {
        SUB_COMMAND_LOG.generatingMockGenesis(
            genesisParams.validatorCount, genesisParams.genesisTime);
      }

      final long genesisTime = genesisParams.genesisTime;
      final BeaconState genesisState =
          new GenesisStateBuilder()
              .spec(spec)
              .genesisTime(genesisTime)
              .addMockValidators(genesisParams.validatorCount)
              .build();

      if (outputToFile) {
        SUB_COMMAND_LOG.storingGenesis(genesisParams.outputFile, false);
      }
      fileStream.write(genesisState.sszSerialize().toArrayUnsafe());
      if (outputToFile) {
        SUB_COMMAND_LOG.storingGenesis(genesisParams.outputFile, true);
      }
    } finally {
      if (fileStream instanceof FileOutputStream) {
        fileStream.close();
      }
    }
  }

  public static class MockGenesisParams {
    @Option(
        names = {"-o", "--output-file"},
        paramLabel = "<FILENAME>",
        description = "Path/filename of the output file\nDefault: stdout")
    private String outputFile = null;

    @Option(
        names = {"-v", "--validator-count"},
        paramLabel = "<VALIDATOR_COUNT>",
        description = "The number of validators to include")
    private int validatorCount = 64;

    @Option(
        names = {"-t", "--genesis-time"},
        paramLabel = "<GENESIS_TIME>",
        description = "The genesis time")
    private long genesisTime = System.currentTimeMillis() / 1000;
  }
}
