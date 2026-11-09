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

package tech.pegasys.teku.cli.subcommand.debug;

import com.google.common.base.Throwables;
import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.commons.lang3.tuple.Pair;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import picocli.CommandLine;
import tech.pegasys.teku.bls.keystore.KeyStoreValidationException;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.ValidatorKeysOptions;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.validator.client.loader.LocalValidatorVerifier;
import tech.pegasys.teku.validator.client.loader.ValidatorSource;

@CommandLine.Command(
    name = "key-check",
    aliases = {"kc"},
    description = "Check attributes of a validator key, and where it is running from.",
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class ValidatorKeysCheckCommand implements Callable<Integer> {
  public static final SubCommandLogger SUB_COMMAND_LOG = new SubCommandLogger();

  @CommandLine.Mixin(name = "Validator Keys")
  private ValidatorKeysOptions validatorKeysOptions;

  @CommandLine.Option(
      names = {"--verbose-output-enabled", "--v", "--verbose"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      fallbackValue = "true",
      description = "Verbose output should be displayed.",
      arity = "0..1")
  private boolean isVerboseOutputEnabled = false;

  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  private AsyncRunner asyncRunner;

  private LocalValidatorVerifier verifier;

  private List<Pair<Path, Path>> filePairs;

  @Override
  public Integer call() {
    final TekuConfiguration config = tekuConfiguration();

    final AsyncRunnerFactory asyncRunnerFactory =
        AsyncRunnerFactory.createDefault(new MetricTrackingExecutorFactory(metricsSystem));
    asyncRunner = asyncRunnerFactory.create("keychecker", 2);
    verifier =
        new LocalValidatorVerifier(
            SpecFactory.create("mainnet"),
            config.validatorClient().getValidatorConfig().getValidatorKeys(),
            asyncRunner);

    if (!filesLocatorCanParseEntries()) {
      return 1;
    }

    if (!keysCanBeLoadedAndLocked()) {
      return 1;
    }

    SUB_COMMAND_LOG.display("Successfully completed checking keystore files.");
    return 0;
  }

  private boolean keysCanBeLoadedAndLocked() {
    int failedKeys = 0;
    int successfulKeys = 0;
    for (final Pair<Path, Path> keystorePasswordPathPair : filePairs) {
      try {
        if (isVerboseOutputEnabled) {
          SUB_COMMAND_LOG.display(
              String.format(" -> Creating provider from %s", keystorePasswordPathPair.getKey()));
        }
        final ValidatorSource.ValidatorProvider provider =
            verifier.createValidatorProvider(keystorePasswordPathPair);
        if (canCreateSignerFromValidatorProvider(provider)) {
          successfulKeys++;
        } else {
          failedKeys++;
        }
      } catch (final KeyStoreValidationException e) {
        if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
          SUB_COMMAND_LOG.error(
              String.format(
                  "Unable to load keystore: %s\nCheck that the files have been correctly specified, and permissions are correct",
                  e.getMessage()));
          failedKeys++;
        }
      } catch (final InvalidConfigurationException e) {
        if (Throwables.getRootCause(e) instanceof AccessDeniedException) {
          SUB_COMMAND_LOG.error(
              String.format(
                  "Unable to load keystore: %s\nCheck file permissions on the key and password.",
                  e.getMessage()));
        }
        failedKeys++;
      }
    }

    if (failedKeys == 0) {
      SUB_COMMAND_LOG.display(String.format("Loaded %d keys successfully", successfulKeys));
    } else if (successfulKeys == 0) {
      SUB_COMMAND_LOG.error(String.format("Failed to load %d keys", failedKeys));
    } else {
      SUB_COMMAND_LOG.error(
          String.format(
              "Failed to load %d keys, but %d were loaded successfully.",
              failedKeys, successfulKeys));
    }
    return failedKeys == 0;
  }

  private boolean filesLocatorCanParseEntries() {
    try {
      filePairs = verifier.parse();
    } catch (final InvalidConfigurationException ex) {
      SUB_COMMAND_LOG.error(
          String.format("Failed to load available validators: %s", ex.getMessage()));
      return false;
    }
    return true;
  }

  private boolean canCreateSignerFromValidatorProvider(
      final ValidatorSource.ValidatorProvider validatorProvider) {
    try {
      if (isVerboseOutputEnabled) {
        SUB_COMMAND_LOG.display(
            String.format("  + Creating signer for %s", validatorProvider.getPublicKey()));
      }
      validatorProvider.createSigner();
    } catch (InvalidConfigurationException ex) {
      SUB_COMMAND_LOG.error(
          String.format(
              "Failed to load signer %s: %s", validatorProvider.getPublicKey(), ex.getMessage()));
      return false;
    } catch (final UncheckedIOException ex) {
      SUB_COMMAND_LOG.error(
          String.format(
              "Failed to load signer %s: %s\nCheck the folder permissions for write access.",
              validatorProvider.getPublicKey(), ex.getMessage()));
      return false;
    }
    return true;
  }

  private TekuConfiguration tekuConfiguration() {

    final TekuConfiguration.Builder builder =
        TekuConfiguration.builder().metrics(b -> b.metricsEnabled(false));

    validatorKeysOptions.configure(builder);

    // we don't use the data path, but keep configuration happy.
    builder.data(config -> config.dataBasePath(Path.of(".")));
    builder.validator(config -> config.validatorKeystoreLockingEnabled(false));
    return builder.build();
  }
}
