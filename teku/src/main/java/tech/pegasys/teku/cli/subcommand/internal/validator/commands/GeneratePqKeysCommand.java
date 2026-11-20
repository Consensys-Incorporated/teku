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

package tech.pegasys.teku.cli.subcommand.internal.validator.commands;

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.teku.infrastructure.logging.SubCommandLogger.SUB_COMMAND_LOG;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes48;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.keystore.KeyStore;
import tech.pegasys.teku.bls.keystore.KeyStoreLoader;
import tech.pegasys.teku.bls.keystore.KeyStoreValidationException;
import tech.pegasys.teku.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.KeystorePasswordOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.ValidatorPasswordOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.VerbosityOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.PqKeyGenerator;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.PqKeyGenerator.GeneratedPqKey;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.PqKeyGenerator.PqKeyPaths;
import tech.pegasys.teku.infrastructure.crypto.SecureRandomProvider;
import tech.pegasys.teku.spec.signatures.xmss.PqValidatorPublicData;
import tech.pegasys.teku.validator.api.KeyStoreFilesLocator;
import tech.pegasys.teku.validator.client.loader.PqKeyFiles;
import tech.pegasys.teku.xmss.XmssPublicKey;

@Command(
    name = "generate-pq-keys",
    description =
        "[WARNING: NOT FOR PRODUCTION USE] Generate PQ validator keys: a leanXMSS key and a VRF"
            + " tree for each validator, saved in a versioned keystore format that is not yet"
            + " standardised. Deriving each VRF tree hashes about 2^32 leaves and takes minutes.",
    hidden = true,
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    abbreviateSynopsis = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class GeneratePqKeysCommand implements Callable<Integer> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Spec private CommandSpec spec;

  @Option(
      names = {"--bls-keystores"},
      paramLabel = "<KEY_DIR>:<PASS_DIR> | <KEY_FILE>:<PASS_FILE>",
      description =
          "Generate PQ keys for existing BLS validators, as given to --validator-keys. Each"
              + " validator's PQ keystore and VRF cache are written next to its BLS keystore,"
              + " encrypted with the same password, where the validator client loads them.",
      split = ",",
      arity = "1..*")
  private List<String> blsKeystores = new ArrayList<>();

  @Option(
      names = {"--number-of-validators"},
      paramLabel = "<NUMBER>",
      description =
          "Generate PQ keys for this many new validators with no BLS key. The validator client"
              + " does not load these yet.")
  private Integer validatorCount;

  @Option(
      names = {"--keys-output-path"},
      paramLabel = "<DIR>",
      description =
          "Directory for the keys of new validators (--number-of-validators). Defaults to the"
              + " current directory.")
  private Path outputPath;

  @ArgGroup(heading = "Password for the keystores of new validators:%n")
  private ValidatorPasswordOptions validatorPasswordOptions;

  @Option(
      names = {"--registration-data-file"},
      paramLabel = "<FILE>",
      description =
          "Write the public values to register for each validator (XMSS public key, VRF root,"
              + " EIP-8483 pq_data and the post-fork deposit pubkey and signature) to this JSON"
              + " file.")
  private Path registrationDataFile;

  @Mixin private VerbosityOptions verbosityOptions;

  private final Function<Consumer<String>, PqKeyGenerator> generatorFactory;
  private final Function<String, String> envSupplier;

  public GeneratePqKeysCommand() {
    this(
        output -> new PqKeyGenerator(SecureRandomProvider.createSecureRandom(), output),
        System::getenv);
  }

  @VisibleForTesting
  GeneratePqKeysCommand(
      final Function<Consumer<String>, PqKeyGenerator> generatorFactory,
      final Function<String, String> envSupplier) {
    this.generatorFactory = generatorFactory;
    this.envSupplier = envSupplier;
  }

  @Override
  public Integer call() {
    SUB_COMMAND_LOG.commandIsNotSafeForProduction();
    if (blsKeystores.isEmpty() == (validatorCount == null)) {
      throw new ParameterException(
          spec.commandLine(), "Specify exactly one of --bls-keystores or --number-of-validators");
    }
    final List<Target> targets =
        blsKeystores.isEmpty() ? newValidatorTargets() : blsValidatorTargets();
    if (registrationDataFile != null && Files.exists(registrationDataFile)) {
      throw new ParameterException(
          spec.commandLine(), "Registration data file already exists: " + registrationDataFile);
    }

    final Consumer<String> output =
        verbosityOptions.isVerboseOutputEnabled() ? SUB_COMMAND_LOG::display : message -> {};
    final PqKeyGenerator generator = generatorFactory.apply(output);
    final List<GeneratedPqKey> generated = new ArrayList<>();
    for (int i = 0; i < targets.size(); i++) {
      final Target target = targets.get(i);
      final String label = "Validator " + (i + 1) + "/" + targets.size();
      generated.add(
          generator.generate(label, target.pathsForKey(), target.password(), target.blsKey()));
    }
    if (registrationDataFile != null) {
      writeRegistrationData(generated);
    }
    SUB_COMMAND_LOG.display(
        "Generated PQ keys for "
            + generated.size()
            + " validators. Register each validator's pq_data before the PQ fork.");
    return 0;
  }

  /** Keys for existing BLS validators, written next to their keystores. */
  private List<Target> blsValidatorTargets() {
    final List<Pair<Path, Path>> keystores;
    try {
      keystores = new KeyStoreFilesLocator(blsKeystores, File.pathSeparator).parse();
    } catch (final RuntimeException e) {
      throw new ParameterException(spec.commandLine(), e.getMessage(), e);
    }
    final List<Target> targets = new ArrayList<>();
    for (final Pair<Path, Path> keystore : keystores) {
      final Path keystorePath = keystore.getLeft();
      final PqKeyPaths paths =
          new PqKeyPaths(
              PqKeyFiles.keystorePath(keystorePath), PqKeyFiles.vrfCachePath(keystorePath));
      checkNoneExist(paths);
      final KeyStoreData keyStoreData;
      try {
        keyStoreData = KeyStoreLoader.loadFromFile(keystorePath.toUri());
      } catch (final KeyStoreValidationException e) {
        throw new ParameterException(
            spec.commandLine(), "Invalid BLS keystore " + keystorePath + ": " + e.getMessage(), e);
      }
      final String password =
          KeystorePasswordOptions.readFromFile(spec.commandLine(), keystore.getRight().toFile());
      if (!KeyStore.validatePassword(password, keyStoreData)) {
        throw new ParameterException(
            spec.commandLine(),
            "Password " + keystore.getRight() + " does not decrypt BLS keystore " + keystorePath);
      }
      final BLSPublicKey blsPublicKey =
          BLSPublicKey.fromBytesCompressedValidate(Bytes48.wrap(keyStoreData.getPubkey()));
      targets.add(new Target(ignored -> paths, password, Optional.of(blsPublicKey)));
    }
    return targets;
  }

  /** Keys for new validators without a BLS key, named after their XMSS public keys. */
  private List<Target> newValidatorTargets() {
    if (validatorCount <= 0) {
      throw new ParameterException(spec.commandLine(), "--number-of-validators must be positive");
    }
    if (validatorPasswordOptions == null) {
      throw new ParameterException(
          spec.commandLine(),
          "New validators need a keystore password: use"
              + " --encrypted-keystore-validator-password-file or"
              + " --encrypted-keystore-validator-password-env");
    }
    final String password =
        validatorPasswordOptions.getPasswordFile() != null
            ? KeystorePasswordOptions.readFromFile(
                spec.commandLine(), validatorPasswordOptions.getPasswordFile())
            : KeystorePasswordOptions.readFromEnvironmentVariable(
                spec.commandLine(),
                envSupplier,
                validatorPasswordOptions.getPasswordEnvironmentVariable());
    final Path directory = outputPath == null ? Path.of(".") : outputPath;
    try {
      Files.createDirectories(directory);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    final Function<XmssPublicKey, PqKeyPaths> pathsForKey =
        xmssPublicKey -> {
          final String name = "pq_" + xmssPublicKey.toBytes().toUnprefixedHexString();
          return new PqKeyPaths(
              PqKeyFiles.keystorePath(directory, name), PqKeyFiles.vrfCachePath(directory, name));
        };
    final List<Target> targets = new ArrayList<>();
    for (int i = 0; i < validatorCount; i++) {
      targets.add(new Target(pathsForKey, password, Optional.empty()));
    }
    return targets;
  }

  private void checkNoneExist(final PqKeyPaths paths) {
    try {
      paths.checkNoneExist();
    } catch (final UncheckedIOException e) {
      throw new ParameterException(spec.commandLine(), e.getCause().getMessage(), e);
    }
  }

  private void writeRegistrationData(final List<GeneratedPqKey> generated) {
    final List<Map<String, String>> entries = new ArrayList<>();
    for (final GeneratedPqKey key : generated) {
      final PqValidatorPublicData publicData = key.publicData();
      final Map<String, String> entry = new LinkedHashMap<>();
      key.blsPublicKey()
          .ifPresent(blsKey -> entry.put("bls_pubkey", blsKey.toBytesCompressed().toHexString()));
      entry.put("xmss_pubkey", publicData.xmssPublicKey().toBytes().toHexString());
      entry.put("vrf_root", publicData.vrfRoot().toHexString());
      entry.put("pq_data", publicData.getPqData().toHexString());
      entry.put("deposit_pubkey", publicData.getDepositPubkey().toHexString());
      entry.put("deposit_signature", publicData.getDepositSignature().toHexString());
      entry.put("keystore", key.paths().keystore().toString());
      entries.add(entry);
    }
    try {
      Files.writeString(
          registrationDataFile,
          OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(entries),
          UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    SUB_COMMAND_LOG.display("Registration data written to " + registrationDataFile);
  }

  private record Target(
      Function<XmssPublicKey, PqKeyPaths> pathsForKey,
      String password,
      Optional<BLSPublicKey> blsKey) {}
}
