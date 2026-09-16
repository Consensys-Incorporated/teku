/*
 * Copyright Consensys Software Inc., 2026
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

import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_VALIDATOR_EXECUTOR_THREADS;
import static tech.pegasys.teku.validator.api.ValidatorConfig.DEFAULT_DOPPELGANGER_DETECTION_ENABLED;
import static tech.pegasys.teku.validator.api.ValidatorConfig.DEFAULT_SHUTDOWN_WHEN_VALIDATOR_SLASHED_ENABLED;
import static tech.pegasys.teku.validator.api.ValidatorConfig.DEFAULT_VALIDATOR_IS_LOCAL_SLASHING_PROTECTION_SYNCHRONIZED_ENABLED;
import static tech.pegasys.teku.validator.api.ValidatorConfig.DEFAULT_VALIDATOR_SLASHING_PROTECTION_STRICT_MODE_ENABLED;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import org.apache.tuweni.bytes.Bytes32;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.converter.GraffitiConverter;
import tech.pegasys.teku.cli.converter.OptionalIntConverter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.api.ValidatorPerformanceTrackingMode;

public class ValidatorOptions {

  @Mixin private final ValidatorKeysOptions validatorKeysOptions = new ValidatorKeysOptions();

  @Mixin
  private final ValidatorProposerOptions validatorProposerOptions = new ValidatorProposerOptions();

  @Mixin private final BuilderOptions builderOptions = new BuilderOptions();

  @Option(
      names = {"--validators-graffiti"},
      converter = GraffitiConverter.class,
      paramLabel = "<GRAFFITI STRING>",
      description =
          "Graffiti value to include during block creation. Value gets converted to bytes and padded to Bytes32.",
      arity = "1")
  private Bytes32 graffiti = ValidatorConfig.DEFAULT_GRAFFITI.orElse(null);

  @Option(
      names = {"--validators-graffiti-file"},
      paramLabel = "<GRAFFITI FILE>",
      description =
          "File to load graffiti value to include during block creation. Value gets converted to bytes and padded to Bytes32. "
              + "Takes precedence over --validators-graffiti. If the file can not be read, the --validators-graffiti value is used as a fallback.",
      arity = "1")
  private Path graffitiFile;

  @Option(
      names = {"--validators-graffiti-client-append-format"},
      paramLabel = "<STRING>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Appends CL and EL clients information with a space to user's graffiti "
              + "when producing a block on the Beacon Node. (Valid values: ${COMPLETION-CANDIDATES})",
      arity = "1")
  private ClientGraffitiAppendFormat clientGraffitiAppendFormat =
      ValidatorConfig.DEFAULT_CLIENT_GRAFFITI_APPEND_FORMAT;

  @Option(
      names = {"--validators-performance-tracking-mode"},
      paramLabel = "<TRACKING_MODE>",
      description =
          "Set strategy for handling performance tracking. "
              + "Valid values: ${COMPLETION-CANDIDATES}",
      arity = "1")
  private ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode =
      ValidatorPerformanceTrackingMode.DEFAULT_MODE;

  @Option(
      names = {"--validators-keystore-locking-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      fallbackValue = "true",
      description = "Enable locking validator keystore files",
      arity = "0..1")
  private boolean validatorKeystoreLockingEnabled =
      ValidatorConfig.DEFAULT_VALIDATOR_KEYSTORE_LOCKING_ENABLED;

  @Option(
      names = {"--validators-external-signer-slashing-protection-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enable internal slashing protection for external signers",
      fallbackValue = "true",
      arity = "0..1")
  private boolean validatorExternalSignerSlashingProtectionEnabled =
      ValidatorConfig.DEFAULT_VALIDATOR_EXTERNAL_SIGNER_SLASHING_PROTECTION_ENABLED;

  @Option(
      names = {"--validators-early-attestations-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Generate attestations as soon as a block is known, rather than delaying until the attestation is due",
      fallbackValue = "true",
      arity = "0..1")
  private boolean generateEarlyAttestations = ValidatorConfig.DEFAULT_GENERATE_EARLY_ATTESTATIONS;

  @Option(
      names = {"--Xvalidator-executor-max-queue-size"},
      paramLabel = "<INTEGER>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Set the maximum queue size of the validator executor",
      hidden = true,
      converter = OptionalIntConverter.class,
      arity = "1")
  private OptionalInt executorMaxQueueSize = OptionalInt.empty();

  @Option(
      names = {"--doppelganger-detection-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enable validators doppelganger detection",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      arity = "0..1",
      fallbackValue = "true")
  private boolean doppelgangerDetectionEnabled = DEFAULT_DOPPELGANGER_DETECTION_ENABLED;

  @Option(
      names = {"--Xvalidator-client-executor-threads"},
      paramLabel = "<INTEGER>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Set the number of threads for the validator executor",
      hidden = true,
      arity = "1")
  private int executorThreads = DEFAULT_VALIDATOR_EXECUTOR_THREADS;

  @Option(
      names = {"--Xvalidator-client-beacon-api-executor-threads"},
      paramLabel = "<INTEGER>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Set the number of threads for the validator beacon node API executor",
      hidden = true,
      converter = OptionalIntConverter.class,
      arity = "1")
  private OptionalInt beaconApiExecutorThreads = OptionalInt.empty();

  @Option(
      names = {"--Xvalidator-client-beacon-api-readiness-executor-threads"},
      paramLabel = "<INTEGER>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Set the number of threads for the validator beacon node API readiness executor",
      hidden = true,
      converter = OptionalIntConverter.class,
      arity = "1")
  private OptionalInt beaconApiReadinessExecutorThreads = OptionalInt.empty();

  @Option(
      names = {"--exit-when-no-validator-keys-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enable terminating the process if no validator keys are found during startup",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      arity = "0..1",
      fallbackValue = "true")
  private boolean exitWhenNoValidatorKeysEnabled =
      ValidatorConfig.DEFAULT_EXIT_WHEN_NO_VALIDATOR_KEYS_ENABLED;

  @Option(
      names = {"--validator-is-local-slashing-protection-synchronized-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Restrict local signing to a single operation at a time.",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      arity = "0..1",
      fallbackValue = "true")
  private boolean isLocalSlashingProtectionSynchronizedEnabled =
      DEFAULT_VALIDATOR_IS_LOCAL_SLASHING_PROTECTION_SYNCHRONIZED_ENABLED;

  @Option(
      names = {"--shut-down-when-validator-slashed-enabled"},
      paramLabel = "<BOOLEAN>",
      description =
          "If enabled and an owned validator key is detected as slashed, the node will terminate. In this case, the service should not be restarted.",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      arity = "0..1",
      fallbackValue = "true")
  private boolean shutdownWhenValidatorSlashed = DEFAULT_SHUTDOWN_WHEN_VALIDATOR_SLASHED_ENABLED;

  @Option(
      names = {"--slashing-protection-strict-mode-enabled"},
      paramLabel = "<BOOLEAN>",
      description =
          "If enabled, Teku will refuse to sign if slashing protection data for a validator is not found.",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      arity = "0..1",
      fallbackValue = "true")
  private boolean slashingProtectionStrictModeEnabled =
      DEFAULT_VALIDATOR_SLASHING_PROTECTION_STRICT_MODE_ENABLED;

  public void configure(final TekuConfiguration.Builder builder) {
    builder.validator(
        config ->
            config
                .validatorKeystoreLockingEnabled(validatorKeystoreLockingEnabled)
                .validatorPerformanceTrackingMode(validatorPerformanceTrackingMode)
                .validatorExternalSignerSlashingProtectionEnabled(
                    validatorExternalSignerSlashingProtectionEnabled)
                .isLocalSlashingProtectionSynchronizedModeEnabled(
                    isLocalSlashingProtectionSynchronizedEnabled)
                .graffitiProvider(
                    new FileBackedGraffitiProvider(
                        Optional.ofNullable(graffiti), Optional.ofNullable(graffitiFile)))
                .clientGraffitiAppendFormat(clientGraffitiAppendFormat)
                .generateEarlyAttestations(generateEarlyAttestations)
                .doppelgangerDetectionEnabled(doppelgangerDetectionEnabled)
                .executorThreads(executorThreads)
                .exitWhenNoValidatorKeysEnabled(exitWhenNoValidatorKeysEnabled)
                .shutdownWhenValidatorSlashedEnabled(shutdownWhenValidatorSlashed)
                .slashingProtectionStrictModeEnabled(slashingProtectionStrictModeEnabled)
                .executorMaxQueueSize(executorMaxQueueSize)
                .beaconApiExecutorThreads(beaconApiExecutorThreads)
                .beaconApiReadinessExecutorThreads(beaconApiReadinessExecutorThreads));
    validatorProposerOptions.configure(builder);
    validatorKeysOptions.configure(builder);
    builderOptions.configure(builder);
  }
}
