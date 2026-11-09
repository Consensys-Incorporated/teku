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

import static tech.pegasys.teku.cli.subcommand.RemoteSpecLoader.getSpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.ValidatorClientDataOptions;
import tech.pegasys.teku.cli.options.ValidatorClientOptions;
import tech.pegasys.teku.cli.options.ValidatorKeysOptions;
import tech.pegasys.teku.cli.subcommand.debug.PrettyPrintCommand;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.RejectingSlashingProtector;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.loader.HttpClientExternalSignerFactory;
import tech.pegasys.teku.validator.client.loader.PublicKeyLoader;
import tech.pegasys.teku.validator.client.loader.SlashingProtectionLogger;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.remote.typedef.OkHttpValidatorMinimalTypeDefClient;

@CommandLine.Command(
    name = "voluntary-exit",
    description = "Create and sign a voluntary exit for a specified validator.",
    showDefaultValues = true,
    mixinStandardHelpOptions = true,
    abbreviateSynopsis = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class VoluntaryExitCommand implements Callable<Integer> {

  public static final SubCommandLogger SUB_COMMAND_LOG = new SubCommandLogger();
  private OkHttpValidatorMinimalTypeDefClient typeDefClient;
  private Fork fork;
  private Bytes32 genesisRoot;
  private Map<BLSPublicKey, Validator> validatorsMap;
  private TekuConfiguration config;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private Spec spec;
  private final TimeProvider timeProvider = new SystemTimeProvider();

  static final String WITHDRAWALS_PERMANENT_MESASGE =
      "These validators won't be able to be re-activated once this operation is complete.";
  static final String WITHDRAWALS_NOT_ACTIVE =
      "NOTE: Withdrawals will not be possible until the Capella network fork.";

  private Optional<List<BLSPublicKey>> maybePubKeysToExit = Optional.empty();

  @CommandLine.Mixin(name = "Validator Keys")
  private ValidatorKeysOptions validatorKeysOptions;

  @CommandLine.Mixin(name = "Validator Client")
  private ValidatorClientOptions validatorClientOptions;

  @CommandLine.Option(
      names = {"--epoch"},
      paramLabel = "<EPOCH>",
      description =
          "Earliest epoch that the voluntary exit can be processed. Defaults to current epoch.",
      arity = "1")
  private UInt64 epoch;

  @CommandLine.Option(
      names = {"--confirmation-enabled"},
      description = "Request confirmation before submitting voluntary exits.",
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      fallbackValue = "true",
      arity = "1")
  private boolean confirmationEnabled = true;

  @CommandLine.Mixin(name = "Data")
  private ValidatorClientDataOptions dataOptions;

  @CommandLine.Option(
      names = {"-n", "--network"},
      paramLabel = "<NETWORK>",
      description = "Represents which network to use.",
      arity = "1")
  private String network;

  @CommandLine.Option(
      names = {"--validator-public-keys"},
      description =
          "Optionally restrict the exit command to a specified list of public keys. When the parameter is not used, all keys will be exited.",
      paramLabel = "<STRINGS>",
      split = ",",
      arity = "0..1")
  private List<String> validatorPublicKeys;

  @CommandLine.Option(
      names = {"--include-keymanager-keys"},
      description = "Include validator keys managed via keymanager APIs.",
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      fallbackValue = "true",
      arity = "0..1")
  private boolean includeKeyManagerKeys = false;

  @CommandLine.Option(
      names = {"--save-exits-path"},
      description =
          "Save the generated exit messages to the specified path, don't validate exit epoch, and skip publishing them.",
      paramLabel = "<FOLDER>",
      arity = "1")
  public File voluntaryExitsFolder;

  private AsyncRunnerFactory asyncRunnerFactory;

  @Override
  public Integer call() {
    SUB_COMMAND_LOG.display("Loading configuration...");
    try {
      initialise();

      if (voluntaryExitsFolder != null) {
        SUB_COMMAND_LOG.display(
            "Saving exits to folder "
                + voluntaryExitsFolder
                + ", and not submitting to beacon-node.");
        if (!saveExitsToFolder()) {
          return 1;
        }
      } else {
        if (confirmationEnabled) {
          if (!confirmExits()) {
            return 1;
          }
        }
        getValidatorIndices(validatorsMap).forEach(this::submitExitForValidator);
      }
    } catch (Exception ex) {
      if (ExceptionUtil.hasCause(ex, ConnectException.class)) {
        SUB_COMMAND_LOG.error(getFailedToConnectMessage());
      } else if (ex instanceof InvalidConfigurationException) {
        SUB_COMMAND_LOG.error(ex.getMessage());
      } else {
        SUB_COMMAND_LOG.error("Fatal error in VoluntaryExit. Exiting", ex);
      }
      return 1;
    } finally {
      if (asyncRunnerFactory != null) {
        asyncRunnerFactory.shutdown();
      }
    }
    return 0;
  }

  private boolean saveExitsToFolder() {
    if (voluntaryExitsFolder.exists() && !voluntaryExitsFolder.isDirectory()) {
      SUB_COMMAND_LOG.error(
          String.format(
              "%s exists and is not a directory, cannot export to this path.",
              voluntaryExitsFolder));
      return false;
    } else if (!voluntaryExitsFolder.exists()) {
      voluntaryExitsFolder.mkdirs();
    }
    final AtomicInteger failures = new AtomicInteger();
    getValidatorIndices(validatorsMap)
        .forEach(
            (publicKey, validatorIndex) -> {
              if (!storeExitForValidator(publicKey, validatorIndex)) {
                failures.incrementAndGet();
              }
            });
    if (failures.get() > 0) {
      return false;
    }
    return true;
  }

  private boolean confirmExits() {
    SUB_COMMAND_LOG.display("Exits are going to be generated for validators: ");
    SUB_COMMAND_LOG.display(getValidatorAbbreviatedKeys());
    SUB_COMMAND_LOG.display("Epoch: " + epoch.toString());
    SUB_COMMAND_LOG.display("");
    SUB_COMMAND_LOG.display(WITHDRAWALS_PERMANENT_MESASGE);
    if (!spec.isMilestoneSupported(SpecMilestone.CAPELLA)) {
      SUB_COMMAND_LOG.display(WITHDRAWALS_NOT_ACTIVE);
    }
    SUB_COMMAND_LOG.display("Are you sure you wish to continue (yes/no)? ");
    Scanner scanner = new Scanner(System.in, Charset.defaultCharset());
    final String confirmation = scanner.next();

    if (!confirmation.equalsIgnoreCase("yes")) {
      SUB_COMMAND_LOG.display("Cancelled sending voluntary exit.");
      return false;
    }
    return true;
  }

  private String getValidatorAbbreviatedKeys() {
    return validatorsMap.keySet().stream()
        .map(BLSPublicKey::toAbbreviatedString)
        .collect(Collectors.joining(", "));
  }

  private Object2IntMap<BLSPublicKey> getValidatorIndices(
      final Map<BLSPublicKey, Validator> validatorsMap) {
    final Object2IntMap<BLSPublicKey> validatorIndices = new Object2IntOpenHashMap<>();
    final List<String> publicKeys =
        validatorsMap.keySet().stream().map(BLSPublicKey::toString).toList();
    typeDefClient
        .postStateValidators(publicKeys)
        .ifPresent(
            validatorList ->
                validatorList.forEach(
                    validator ->
                        validatorIndices.put(
                            validator.getPublicKey(), validator.getIntegerIndex().intValue())));

    publicKeys.forEach(
        key -> {
          if (!validatorIndices.containsKey(BLSPublicKey.fromHexString(key))) {
            SUB_COMMAND_LOG.error("Validator not found: " + key);
          }
        });
    return validatorIndices;
  }

  private void submitExitForValidator(final BLSPublicKey publicKey, final int validatorIndex) {
    try {
      final SignedVoluntaryExit exit = generateSignedExit(publicKey, validatorIndex);
      typeDefClient.sendVoluntaryExit(exit);
      SUB_COMMAND_LOG.display(
          "Exit for validator " + publicKey.toAbbreviatedString() + " submitted.");
    } catch (BadRequestException ex) {
      SUB_COMMAND_LOG.error(
          "Failed to submit exit for validator " + publicKey.toAbbreviatedString());
      SUB_COMMAND_LOG.error(ex.getMessage());
    }
  }

  private SignedVoluntaryExit generateSignedExit(
      final BLSPublicKey publicKey, final int validatorIndex) {
    final ForkInfo forkInfo = new ForkInfo(fork, genesisRoot);
    final VoluntaryExit message = new VoluntaryExit(epoch, UInt64.valueOf(validatorIndex));
    final BLSSignature signature =
        Optional.ofNullable(validatorsMap.get(publicKey))
            .orElseThrow()
            .getSigner()
            .signVoluntaryExit(message, forkInfo)
            .join();
    return new SignedVoluntaryExit(message, signature);
  }

  private boolean storeExitForValidator(
      final BLSPublicKey blsPublicKey, final Integer validatorIndex) {
    final SignedVoluntaryExit exit = generateSignedExit(blsPublicKey, validatorIndex);
    try {
      SUB_COMMAND_LOG.display("Writing signed exit for " + blsPublicKey.toAbbreviatedString());
      Files.writeString(
          voluntaryExitsFolder.toPath().resolve(blsPublicKey.toAbbreviatedString() + "_exit.json"),
          prettyExitMessage(exit));
      return true;
    } catch (IOException e) {
      SUB_COMMAND_LOG.error("Failed to store exit for " + blsPublicKey.toAbbreviatedString());
      return false;
    }
  }

  private String prettyExitMessage(final SignedVoluntaryExit signedVoluntaryExit)
      throws JsonProcessingException {
    final PrettyPrintCommand.OutputFormat json = PrettyPrintCommand.OutputFormat.JSON;
    return JsonUtil.serialize(
        json.createFactory(),
        gen -> {
          gen.useDefaultPrettyPrinter();
          signedVoluntaryExit
              .getSchema()
              .getJsonTypeDefinition()
              .serialize(signedVoluntaryExit, gen);
        });
  }

  private UInt64 getEpochFromGenesisData(final GenesisData genesisData) {
    final UInt64 genesisTime = genesisData.getGenesisTime();
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    final UInt64 slot =
        spec.getGenesisSpec().miscHelpers().computeSlotAtTime(genesisTime, currentTime);
    return spec.computeEpochAtSlot(slot);
  }

  private void initialise() {
    if (validatorPublicKeys != null) {
      this.maybePubKeysToExit =
          Optional.of(validatorPublicKeys.stream().map(BLSPublicKey::fromHexString).toList());
    }
    config = tekuConfiguration();
    asyncRunnerFactory =
        AsyncRunnerFactory.createDefault(new MetricTrackingExecutorFactory(metricsSystem));
    final AsyncRunner asyncRunner = asyncRunnerFactory.create("voluntary_exits", 8);

    typeDefClient =
        config
            .validatorClient()
            .getValidatorConfig()
            .getPrimaryBeaconNodeApiEndpoint()
            .map(RemoteSpecLoader::createTypeDefClient)
            .orElseThrow();

    if (network == null) {
      SUB_COMMAND_LOG.display(
          " - Loading network settings from " + typeDefClient.getBaseEndpoint());
      spec = getSpec(typeDefClient);
    } else {
      SUB_COMMAND_LOG.display(" - Loading local settings for " + network + " network");
      spec = SpecFactory.create(network);
    }

    final Optional<GenesisData> maybeGenesisData = typeDefClient.getGenesis();
    validateOrDefaultEpoch(maybeGenesisData);
    // Exits are validated against the state's fork, so a past --epoch must not drag the signing
    // fork back with it - that produces an exit the beacon node rejects. A future --epoch is
    // still permitted (--save-exits-path skips the epoch check) and keeps signing against the
    // fork at that epoch, so pre-signing for an upcoming fork is unaffected.
    fork =
        spec.getForkSchedule()
            .getFork(
                maybeGenesisData.map(this::getEpochFromGenesisData).map(epoch::max).orElse(epoch));

    // get genesis time
    final Optional<Bytes32> maybeRoot = maybeGenesisData.map(GenesisData::getGenesisValidatorsRoot);
    if (maybeRoot.isEmpty()) {
      throw new InvalidConfigurationException(
          "Unable to fetch genesis data, cannot generate an exit.");
    }
    genesisRoot = maybeRoot.get();

    final RejectingSlashingProtector slashingProtector = new RejectingSlashingProtector();
    final SlashingProtectionLogger slashingProtectionLogger =
        new SlashingProtectionLogger(
            slashingProtector, spec, asyncRunner, ValidatorLogger.VALIDATOR_LOGGER);

    Optional<DataDirLayout> dataDirLayout = Optional.empty();

    if (includeKeyManagerKeys) {
      dataDirLayout = Optional.of(DataDirLayout.createFrom(dataOptions.getDataConfig()));
    }

    final ValidatorConfig validatorConfig = config.validatorClient().getValidatorConfig();
    final Supplier<HttpClient> externalSignerHttpClientFactory =
        HttpClientExternalSignerFactory.create(validatorConfig);

    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            validatorConfig,
            config.validatorClient().getInteropConfig(),
            externalSignerHttpClientFactory,
            new RejectingSlashingProtector(),
            slashingProtectionLogger,
            new PublicKeyLoader(
                externalSignerHttpClientFactory, validatorConfig.getValidatorExternalSignerUrl()),
            asyncRunner,
            metricsSystem,
            dataDirLayout,
            (publicKey) -> Optional.empty());

    validatorLoader.loadValidators();
    final Map<BLSPublicKey, Validator> ownedValidators =
        validatorLoader.getOwnedValidators().getValidators().stream()
            .collect(Collectors.toMap(Validator::getPublicKey, validator -> validator));
    if (maybePubKeysToExit.isPresent()) {
      validatorsMap = new HashMap<>();
      List<BLSPublicKey> pubKeysToExit = maybePubKeysToExit.get();
      ownedValidators.keySet().stream()
          .filter(pubKeysToExit::contains)
          .forEach(
              validatorPubKey ->
                  validatorsMap.putIfAbsent(validatorPubKey, ownedValidators.get(validatorPubKey)));
    } else {
      validatorsMap = ownedValidators;
    }

    if (validatorsMap.isEmpty()) {
      throw new InvalidConfigurationException("No validators were found to exit");
    }
  }

  private void validateOrDefaultEpoch(final Optional<GenesisData> maybeGenesisData) {
    final Optional<UInt64> maybeEpoch = maybeGenesisData.map(this::getEpochFromGenesisData);

    if (epoch == null) {
      if (maybeEpoch.isEmpty()) {
        throw new InvalidConfigurationException(
            "Could not calculate epoch from genesis data, please specify --epoch");
      }
      epoch = maybeEpoch.orElseThrow();
    } else if (maybeEpoch.isPresent()
        && epoch.isGreaterThan(maybeEpoch.get())
        && voluntaryExitsFolder == null) {
      throw new InvalidConfigurationException(
          String.format(
              "The specified epoch %s is greater than current epoch %s, cannot continue.",
              epoch, maybeEpoch.get()));
    }
  }

  private TekuConfiguration tekuConfiguration() {

    final TekuConfiguration.Builder builder =
        TekuConfiguration.builder().metrics(b -> b.metricsEnabled(false));

    validatorKeysOptions.configure(builder);
    validatorClientOptions.configure(builder);

    // we don't use the data path, but keep configuration happy.
    builder.data(config -> config.dataBasePath(Path.of(".")));
    builder.validator(config -> config.validatorKeystoreLockingEnabled(false));
    return builder.build();
  }

  private String getFailedToConnectMessage() {
    return String.format(
        "Failed to connect to beacon node. Check that %s is available and REST API is enabled.",
        config
            .validatorClient()
            .getValidatorConfig()
            .getPrimaryBeaconNodeApiEndpoint()
            .orElse(URI.create("http://127.0.0.1:5051")));
  }
}
