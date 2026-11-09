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

import java.time.Duration;
import picocli.CommandLine;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.services.zkchain.ZkChainConfiguration;

public class ZkChainOptions {
  @CommandLine.Option(
      hidden = true,
      names = {"--Xstateless-validation-enabled"},
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "Enable stateless validation of blocks and states.",
      arity = "0..1",
      fallbackValue = "true")
  private boolean statelessValidationEnabled =
      ZkChainConfiguration.DEFAULT_STATELESS_VALIDATION_ENABLED;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xgenerate-execution-proofs-enabled"},
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "Enable generation of execution proofs for blocks.",
      arity = "0..1",
      fallbackValue = "true")
  private boolean generateExecutionProofsEnabled =
      ZkChainConfiguration.DEFAULT_GENERATE_EXECUTION_PROOFS_ENABLED;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xstateless-min-proofs-required"},
      paramLabel = "<NUMBER>",
      description =
          "Minimum number of execution proofs required for stateless validation. Must be at least 1.",
      arity = "1")
  private int statelessMinProofsRequired =
      ZkChainConfiguration.DEFAULT_STATELESS_MIN_PROOFS_REQUIRED;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xstateless-proofs-generation-delay"},
      paramLabel = "<DURATION>",
      description = "Proof generation artificial delay in milliseconds.",
      arity = "1")
  private long statelessProofGenerationDelay =
      ZkChainConfiguration.DEFAULT_PROOF_GENERATION_DELAY.toMillis();

  public void configure(final TekuConfiguration.Builder builder) {
    builder.zkchain(
        zkChainConfiguration ->
            zkChainConfiguration
                .statelessValidationEnabled(statelessValidationEnabled)
                .generateExecutionProofsEnabled(generateExecutionProofsEnabled)
                .statelessMinProofsRequired(statelessMinProofsRequired)
                .proofDelayDurationInMs(Duration.ofMillis(statelessProofGenerationDelay)));
  }
}
