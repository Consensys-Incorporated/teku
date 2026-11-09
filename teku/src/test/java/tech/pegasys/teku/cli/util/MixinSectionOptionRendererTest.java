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

package tech.pegasys.teku.cli.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.UsageMessageSpec;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;

@SuppressWarnings("unused")
class MixinSectionOptionRendererTest {

  @Test
  void shouldPrintHelpWithSections() throws Exception {
    final CommandLine commandLine = new CommandLine(new TestOptions());
    commandLine
        .getHelpSectionMap()
        .put(UsageMessageSpec.SECTION_KEY_OPTION_LIST, new MixinSectionOptionRenderer());
    final StringWriter stringWriter = new StringWriter();
    commandLine.usage(new PrintWriter(stringWriter));

    final String result = stringWriter.toString();
    final String expected =
        Resources.toString(
            Resources.getResource(MixinSectionOptionRendererTest.class, "TestOptionsHelp.txt"),
            StandardCharsets.UTF_8);
    assertThat(result).isEqualToNormalizingNewlines(expected);
  }

  @Command(
      name = "teku",
      subcommands = {},
      showDefaultValues = true,
      abbreviateSynopsis = true,
      description = "Run the Teku beacon chain client and validator",
      mixinStandardHelpOptions = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%n@|bold Description:|@%n%n",
      optionListHeading = "%n@|bold Options:|@%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  private static class TestOptions {
    @Option(
        names = {"-c", "--config-file"},
        description = "Set a config file",
        arity = "1")
    private String configFile;

    @ArgGroup(heading = "%nActual Arg Group", multiplicity = "0..1")
    private final TestArgGroup argGroup = new TestArgGroup();

    @Mixin(name = "First Mixin")
    private final Mixin1 mixin1 = new Mixin1();

    @Mixin(name = "Second Mixin")
    private final AllHiddenOptions allHiddenOptions = new AllHiddenOptions();
  }

  private static class Mixin1 {
    @Option(
        names = {"--value1"},
        description =
            "Set a value with a really quite long description given that it doesn't actually say anything. Some people would think I'm just testing the wrapping behaviour or something...",
        arity = "1")
    private String value1;

    @Option(
        names = {"--value2"},
        description = "Set a second value",
        arity = "1")
    private String value2;

    @Option(
        names = {"--Xvalue3"},
        hidden = true,
        description = "Ooooo! A hidden value!",
        arity = "1")
    private String value3;
  }

  private static class AllHiddenOptions {
    @Option(
        names = {"--Xoption1"},
        hidden = true,
        description = "Mixin 2 option",
        arity = "1")
    private String value1;

    @Option(
        names = {"--XanotherOption"},
        hidden = true,
        description = "Ooooo! A hidden value!",
        arity = "1")
    private String value3;
  }

  private static class TestArgGroup {

    @Option(
        names = {"--beacon-node-api-endpoint", "--beacon-node-api-endpoints"},
        paramLabel = "<ENDPOINT>",
        description =
            "Beacon Node REST API endpoint(s). If more than one endpoint is defined, the first node"
                + " will be used as a primary and others as failovers.",
        split = ",",
        arity = "1..*")
    List<URI> beaconNodeApiEndpoints;

    @Option(
        names = {"--Xsentry-config-file"},
        paramLabel = "<FILE>",
        description = "Config file with sentry node configuration",
        hidden = true,
        arity = "1")
    String sentryConfigFile = null;
  }
}
