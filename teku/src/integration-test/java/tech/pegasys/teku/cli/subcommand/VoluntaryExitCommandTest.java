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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static tech.pegasys.teku.ethereum.json.types.config.SpecConfigDataMapBuilder.GET_SPEC_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.cli.BeaconNodeCommand;
import tech.pegasys.teku.cli.StartAction;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@ExtendWith(MockServerExtension.class)
public class VoluntaryExitCommandTest {
  private InputStream originalSystemIn;
  private PrintStream originalSystemOut;
  private PrintStream originalSytstemErr;
  private final StringWriter stringWriter = new StringWriter();
  private final PrintWriter outputWriter = new PrintWriter(stringWriter, true);
  private final PrintWriter errorWriter = new PrintWriter(stringWriter, true);

  private ClientAndServer server;

  private final BeaconNodeCommand beaconNodeCommand =
      new BeaconNodeCommand(
          outputWriter,
          errorWriter,
          Collections.emptyMap(),
          mock(StartAction.class),
          mock(LoggingConfigurator.class));

  private final ByteArrayOutputStream stdOut = new ByteArrayOutputStream();

  private final ByteArrayOutputStream stdErr = new ByteArrayOutputStream();

  private final String keyManagerPubKey1 =
      "0x8b0f19f3306930d8a1e85a8084ef2caea044066dedfd3de0c22f28473dd07606da5d205ae09ee20072dc9f9e4fd32d79";
  private final String keyManagerPubKey2 =
      "0x82c2a92c6823d43bf11215eddd0f706f168945b298bf718288cf78fcef81df19cd1e5c9c49e7b8d689722fb409f0c0a4";

  private final String validatorPubKey1 =
      "0xa756543ed1f0bac08480d67b1d5ae30f808db65de40bd2006f41a727e4f4200d732cdbee4c81aa930e2ffdf561f0dd25";

  private final String validatorPubKey2 =
      "0xaa516161fd3d6857ac45a89b7f8f9ba604a8c87642e5ef9ccdab46d7dc4e32518a17a371673758073cb81c0d536cbff1";

  private final String nonExistingKey = BLSTestUtil.randomPublicKey(17).toString();

  private final String exitSubmitted = "Exit for validator %s submitted.";

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final Spec specDeneb = TestSpecFactory.createMinimalDeneb();

  private List<String> commandArgs;

  @BeforeEach
  public void setup(final ClientAndServer server) throws IOException {
    this.server = server;
    configureSuccessfulValidatorResponses();
    originalSystemIn = System.in;
    originalSystemOut = System.out;
    originalSytstemErr = System.err;
    System.setOut(new PrintStream(stdOut));
    System.setErr(new PrintStream(stdErr));

    commandArgs =
        List.of(
            "voluntary-exit",
            "--beacon-node-api-endpoint",
            getMockBeaconServerEndpoint(),
            "--data-validator-path",
            Resources.getResource("tech/pegasys/teku/cli/subcommand/voluntary-exit/validator")
                .getPath(),
            "--validator-keys",
            Resources.getResource(
                        "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-keys/keys")
                    .getPath()
                + File.pathSeparator
                + Resources.getResource(
                        "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-keys/passwords")
                    .getPath());
  }

  @AfterEach
  public void tearDown() {
    server.reset();
    System.setOut(originalSystemOut);
    System.setIn(originalSystemIn);
    System.setErr(originalSytstemErr);
  }

  @Test
  public void shouldExitAllLoadedValidators() throws JsonProcessingException {
    configureSuccessfulSpecResponse();
    configureSuccessfulGenesisResponse();
    configureSuccessfulVoluntaryExitResponse();

    final List<String> args = getCommandArguments(true, false, List.of());
    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(0);
    assertValidatorsExited(
        keyManagerPubKey1, keyManagerPubKey2, validatorPubKey1, validatorPubKey2);
  }

  @Test
  public void shouldExitLoadedValidatorsUsingConfirmationMessage() throws JsonProcessingException {
    setUserInput("yes");
    configureSuccessfulSpecResponse();
    configureSuccessfulGenesisResponse();
    configureSuccessfulVoluntaryExitResponse();

    final List<String> args = getCommandArguments(true, true, List.of());
    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(0);
    assertValidatorsExited(
        keyManagerPubKey1, keyManagerPubKey2, validatorPubKey1, validatorPubKey2);
  }

  @Test
  public void shouldExitValidatorWithPubKeyFromKeyManagerOnly() throws JsonProcessingException {
    configureSuccessfulSpecResponse();
    configureSuccessfulGenesisResponse();
    configureSuccessfulVoluntaryExitResponse();

    final List<String> args =
        getCommandArguments(
            true,
            false,
            List.of("--validator-public-keys", keyManagerPubKey2 + "," + nonExistingKey));
    final int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(0);
    assertValidatorsExited(keyManagerPubKey2);
    assertValidatorsNotExited(
        keyManagerPubKey1, validatorPubKey1, validatorPubKey2, nonExistingKey);
  }

  @Test
  public void shouldAcceptNetworkOnCommandLine() {
    configureSuccessfulVoluntaryExitResponse();
    configureSuccessfulGenesisResponse();

    // No beacon-api offered by spec, so would need to be loaded from local network option
    final List<String> args =
        getCommandArguments(
            true,
            false,
            List.of("--network", "minimal", "--validator-public-keys", validatorPubKey1));
    final int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(0);
    assertThat(stdOut.toString(UTF_8)).contains("Loading local settings for minimal network");
    assertValidatorsExited(validatorPubKey1);
  }

  @Test
  public void shouldReturnRejectedReasonWhenExitIsRejectedByBeaconNode() throws IOException {
    configureRejectedVoluntaryExitResponse();
    configureSuccessfulGenesisResponse();

    final List<String> args =
        getCommandArguments(
            false,
            false,
            List.of("--network", "minimal", "--validator-public-keys", validatorPubKey1));

    final int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(0);
    assertThat(stdErr.toString(UTF_8))
        .contains("Operation rejected reason"); // value from rejected-exit.json
    assertValidatorsNotExited(validatorPubKey1);
  }

  @Test
  public void shouldFailToRunExitWithoutASpec() {
    // Network unset, no http service to fetch from
    final List<String> args =
        getCommandArguments(true, false, List.of("--validator-public-keys", validatorPubKey1));
    final int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(1);
    assertThat(stdOut.toString(UTF_8)).contains("Loading network settings from");
  }

  @Test
  public void shouldExitValidatorWithPubKeyFromPathOnly() throws JsonProcessingException {
    configureSuccessfulSpecResponse();
    configureSuccessfulGenesisResponse();
    configureSuccessfulVoluntaryExitResponse();

    final List<String> args =
        getCommandArguments(
            true,
            false,
            List.of("--validator-public-keys", validatorPubKey1 + "," + nonExistingKey));
    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(0);
    assertValidatorsExited(validatorPubKey1);
    assertValidatorsNotExited(
        validatorPubKey2, keyManagerPubKey1, keyManagerPubKey2, nonExistingKey);
  }

  @Test
  public void shouldSkipKeyManagerKeys() throws JsonProcessingException {
    configureSuccessfulSpecResponse();
    configureSuccessfulGenesisResponse();
    configureSuccessfulVoluntaryExitResponse();

    final List<String> args =
        getCommandArguments(
            false,
            false,
            List.of("--validator-public-keys", validatorPubKey1 + "," + nonExistingKey));
    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(0);
    assertValidatorsExited(validatorPubKey1);
    assertValidatorsNotExited(
        validatorPubKey2, keyManagerPubKey1, keyManagerPubKey2, nonExistingKey);
  }

  @Test
  void shouldNotWarn_NotWithdrawableIfCapellaEnabled() throws JsonProcessingException {
    configureSuccessfulSpecResponse(TestSpecFactory.createMinimalCapella());
    configureSuccessfulGenesisResponse();

    final List<String> args = getCommandArguments(false, true, List.of());
    setUserInput("no");
    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(1);
    assertThat(stdOut.toString(UTF_8)).contains(VoluntaryExitCommand.WITHDRAWALS_PERMANENT_MESASGE);
    assertThat(stdOut.toString(UTF_8)).doesNotContain(VoluntaryExitCommand.WITHDRAWALS_NOT_ACTIVE);
    assertValidatorsNotExited(
        validatorPubKey1, validatorPubKey2, keyManagerPubKey1, keyManagerPubKey2, nonExistingKey);
  }

  @Test
  void shouldGenerateExitWithoutSendingToNode(@TempDir final Path tempDir) throws IOException {
    configureSuccessfulSpecResponse(TestSpecFactory.createMinimalCapella());
    configureSuccessfulGenesisResponse();
    final Path outputFolder = tempDir.resolve("out");
    final List<String> args = new ArrayList<>();
    args.addAll(commandArgs);
    args.addAll(
        List.of(
            "--validator-public-keys",
            validatorPubKey1,
            "--save-exits-path",
            outputFolder.toAbsolutePath().toString()));
    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));
    assertThat(parseResult).isEqualTo(0);
    assertThat(stdErr.toString(UTF_8)).isEmpty();

    final String outString = stdOut.toString(UTF_8);
    assertThat(outString).contains("Saving exits to folder " + outputFolder);
    // specifically we wrote validator 1's exit message
    assertThat(outString).contains("Writing signed exit for a756543");
    // generically, we only wrote 1 signed exit to file
    assertThat(StringUtils.countMatches(outString, "Writing signed exit for")).isEqualTo(1);
    // we succeeded in writing a file
    assertThat(outputFolder.resolve("a756543_exit.json").toFile().exists()).isTrue();
    assertValidatorsNotExited(validatorPubKey1);
  }

  @Test
  void shouldFailIfSaveFolderCannotBeCreated(@TempDir final Path tempDir) throws IOException {
    configureSuccessfulSpecResponse(TestSpecFactory.createMinimalCapella());
    configureSuccessfulGenesisResponse();

    final Path invalidOutputDestination = tempDir.resolve("testFile");
    Files.writeString(invalidOutputDestination, "test");
    final List<String> args = new ArrayList<>();
    args.addAll(commandArgs);
    args.addAll(
        List.of(
            "--validator-public-keys",
            validatorPubKey1,
            "--save-exits-path",
            invalidOutputDestination.toAbsolutePath().toString()));
    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));
    assertThat(parseResult).isEqualTo(1);
    assertThat(stdErr.toString(UTF_8))
        .contains("exists and is not a directory, cannot export to this path.");
  }

  @Test
  void shouldFailIfSaveFolderHasInsufficientAccess(@TempDir final Path tempDir) throws IOException {
    configureSuccessfulSpecResponse(TestSpecFactory.createMinimalCapella());
    configureSuccessfulGenesisResponse();

    final Path invalidOutputDestination = tempDir.resolve("testFile");
    tempDir.toFile().mkdir();
    tempDir.toFile().setWritable(false);
    final List<String> args = new ArrayList<>();
    args.addAll(commandArgs);
    args.addAll(
        List.of(
            "--validator-public-keys",
            validatorPubKey1,
            "--save-exits-path",
            invalidOutputDestination.toAbsolutePath().toString()));
    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));
    assertThat(parseResult).isEqualTo(1);
    assertThat(stdErr.toString(UTF_8)).contains("Failed to store exit for a756543");
  }

  @Test
  void shouldFailIfGenesisDataNotAvailableAndNoEpochSpecified() throws JsonProcessingException {
    configureSuccessfulSpecResponse(TestSpecFactory.createMinimalCapella());
    final List<String> args =
        getCommandArguments(false, true, List.of("--validator-public-keys", validatorPubKey1));
    final int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));
    assertThat(parseResult).isEqualTo(1);
    assertThat(stdErr.toString(UTF_8)).contains("Could not calculate epoch from genesis data");
  }

  @Test
  void shouldFailToGenerateExitWithoutBeaconNodeAvailable() {
    final List<String> args =
        List.of("voluntary-exit", "--validator-public-keys", validatorPubKey1);
    final int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));
    assertThat(parseResult).isEqualTo(1);
    assertThat(stdErr.toString(UTF_8)).contains("Failed to connect to beacon node.");
  }

  @Test
  void shouldFailToGenerateIfExternalSignerNotAvailable() throws IOException {
    configureSuccessfulSpecResponse(TestSpecFactory.createMinimalCapella());
    setUserInput("yes");
    configureSuccessfulGenesisResponse();
    final String validator2 = validatorResourceFile("aa51616_response.json");
    setupValidatorStatusResponse(validatorPubKey2, validator2);

    final List<String> args =
        List.of(
            "voluntary-exit",
            commandArgs.get(1),
            commandArgs.get(2),
            "--validators-external-signer-url=" + getMockSignerEndpoint(),
            "--validators-external-signer-public-keys=" + validatorPubKey2);
    final int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));
    assertThat(parseResult).isEqualTo(1);
    // this is not a perfect error, but it does demonstrate it's not saying spec wasn't available,
    // which is a good start
    assertThat(stdErr.toString(UTF_8)).contains("ExternalSignerException");
  }

  @Test
  void canProcessExitForExternalSource(@TempDir final Path tempDir) throws IOException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final List<String> keys = List.of(validatorPubKey2);
    setUserInput("yes");
    configureSuccessfulSpecResponse(TestSpecFactory.createMinimalCapella());
    configureExternalSignerUpcheck();
    configureSuccessfulGenesisResponse();
    configureExternalSignerPublicKeys(keys);

    final String validator2 = validatorResourceFile("aa51616_response.json");
    configureExternalSignerResponse(validatorPubKey2, dataStructureUtil.randomSignature());
    setupValidatorStatusResponse(validatorPubKey2, validator2);

    final List<String> args =
        List.of(
            "voluntary-exit",
            commandArgs.get(1),
            commandArgs.get(2),
            "--save-exits-path",
            tempDir.toAbsolutePath().toString(),
            "--validators-external-signer-url=" + getMockSignerEndpoint(),
            "--validators-external-signer-public-keys=" + keys.get(0));
    final int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));
    assertThat(parseResult).isEqualTo(0);
    assertThat(stdOut.toString(UTF_8)).contains("Writing signed exit for aa51616");
  }

  @Test
  void shouldExitFailureWithNoValidatorKeysFound() throws JsonProcessingException {
    configureSuccessfulSpecResponse();
    configureSuccessfulGenesisResponse();

    final List<String> args = commandArgs.subList(0, 5);
    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(1);
    assertThat(stdErr.toString(UTF_8)).contains("No validators were found to exit");
  }

  @Test
  void shouldExitFailureFutureEpoch() throws IOException {
    configureSuccessfulSpecResponse();
    configureSuccessfulGenesisResponse();

    final List<String> args = getCommandArguments(false, true, List.of("--epoch=1024"));
    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(1);
    assertThat(stdErr.toString(UTF_8))
        .contains("The specified epoch 1024 is greater than current epoch");
  }

  @Test
  void shouldCreateExitForFutureEpochIfOutputFolderDefined(@TempDir final Path tempDir)
      throws IOException {
    configureSuccessfulSpecResponse(TestSpecFactory.createMinimalCapella());
    configureSuccessfulGenesisResponse();
    final List<String> args = new ArrayList<>();
    args.addAll(commandArgs);
    args.addAll(
        List.of(
            "--epoch",
            "1024",
            "--validator-public-keys",
            validatorPubKey1,
            "--save-exits-path",
            tempDir.toAbsolutePath().toString()));

    beaconNodeCommand.parse(args.toArray(new String[0]));
    final String outString = stdOut.toString(UTF_8);
    assertThat(StringUtils.countMatches(outString, "Writing signed exit for")).isEqualTo(1);
  }

  @Test
  void shouldUseCurrentForkDomainForSignatureBeforeDeneb() throws JsonProcessingException {
    setUserInput("yes");
    configureSuccessfulSpecResponse();
    configureSuccessfulGenesisResponse();

    final Supplier<List<SignedVoluntaryExit>> exitsCapture =
        configureSuccessfulVoluntaryExitResponseWithCapture();

    final List<String> args =
        getCommandArguments(false, true, List.of("--validator-public-keys", validatorPubKey1));
    final int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(0);
    assertValidatorsExited(validatorPubKey1);

    final SignedVoluntaryExit signedVoluntaryExit = exitsCapture.get().get(0);
    assertThat(spec.atEpoch(signedVoluntaryExit.getMessage().getEpoch()).getMilestone())
        .isEqualTo(SpecMilestone.BELLATRIX);
    verifyVoluntaryExitSignature(
        signedVoluntaryExit,
        BLSPublicKey.fromHexString(validatorPubKey1),
        spec,
        SpecMilestone.BELLATRIX);
  }

  @Test
  void shouldUseCapellaForkDomainForSignatureAfterCapella() throws JsonProcessingException {
    setUserInput("yes");
    configureSuccessfulDenebSpecResponse();
    configureSuccessfulGenesisResponse();

    final Supplier<List<SignedVoluntaryExit>> exitsCapture =
        configureSuccessfulVoluntaryExitResponseWithCapture();

    final List<String> args =
        getCommandArguments(false, true, List.of("--validator-public-keys", validatorPubKey1));
    final int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(0);
    assertValidatorsExited(validatorPubKey1);

    final SignedVoluntaryExit signedVoluntaryExit = exitsCapture.get().get(0);
    assertThat(specDeneb.atEpoch(signedVoluntaryExit.getMessage().getEpoch()).getMilestone())
        .isEqualTo(SpecMilestone.DENEB);
    verifyVoluntaryExitSignature(
        signedVoluntaryExit,
        BLSPublicKey.fromHexString(validatorPubKey1),
        specDeneb,
        SpecMilestone.CAPELLA);
  }

  private void setUserInput(final String confirmationText) {
    String userInput =
        String.format(confirmationText, System.lineSeparator(), System.lineSeparator());
    ByteArrayInputStream inputStream = new ByteArrayInputStream(userInput.getBytes(UTF_8));
    System.setIn(inputStream);
  }

  /**
   * A past --epoch must not drag the signing fork back with it. The beacon node validates exits
   * against the state's fork, so an exit signed under the fork at the requested epoch would be
   * rejected by the very node that generated it.
   */
  @Test
  void shouldUseCapellaForkDomainForSignatureWithPastEpoch() throws JsonProcessingException {
    // Capella at 1, Deneb at 2, Electra at 3 - so epoch 0 resolves to a pre-Capella milestone.
    final Spec staggeredSpec =
        TestSpecFactory.createMinimalWithCapellaDenebElectraAndFuluForkEpoch(
            UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3), UInt64.valueOf(100_000));
    setUserInput("yes");
    configureSuccessfulSpecResponse(staggeredSpec);
    configureSuccessfulGenesisResponse();

    final Supplier<List<SignedVoluntaryExit>> exitsCapture =
        configureSuccessfulVoluntaryExitResponseWithCapture();

    final List<String> args =
        getCommandArguments(
            false, true, List.of("--validator-public-keys", validatorPubKey1, "--epoch", "0"));
    final int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(0);
    assertValidatorsExited(validatorPubKey1);

    final SignedVoluntaryExit signedVoluntaryExit = exitsCapture.get().get(0);
    assertThat(signedVoluntaryExit.getMessage().getEpoch()).isEqualTo(UInt64.ZERO);
    assertThat(staggeredSpec.atEpoch(UInt64.ZERO).getMilestone())
        .isNotEqualTo(SpecMilestone.CAPELLA);
    // EIP-7044 pins the exit domain to the Capella fork version regardless of the exit's epoch
    verifyVoluntaryExitSignature(
        signedVoluntaryExit,
        BLSPublicKey.fromHexString(validatorPubKey1),
        staggeredSpec,
        SpecMilestone.CAPELLA);
  }

  private List<String> getCommandArguments(
      final boolean includeKeyManagerKeys,
      final boolean confirmationEnabled,
      final List<String> extraArgs) {
    final List<String> args = new ArrayList<>(commandArgs);
    args.add("--include-keymanager-keys=" + includeKeyManagerKeys);
    args.add("--confirmation-enabled=" + confirmationEnabled);

    args.addAll(extraArgs);
    return args;
  }

  private String getMockBeaconServerEndpoint() {
    return String.format("http://127.0.0.1:%s/beacon", server.getLocalPort());
  }

  private String getMockSignerEndpoint() {
    return String.format("http://127.0.0.1:%s/signer", server.getLocalPort());
  }

  private void configureSuccessfulSpecResponse() throws JsonProcessingException {
    configureSuccessfulSpecResponse(spec);
  }

  private void configureSuccessfulDenebSpecResponse() throws JsonProcessingException {
    configureSuccessfulSpecResponse(specDeneb);
  }

  private void configureSuccessfulSpecResponse(final Spec spec) throws JsonProcessingException {
    server
        .when(request().withPath("/eth/v1/config/spec"))
        .respond(response().withStatusCode(200).withBody(getTestSpecJsonString(spec)));
  }

  private void configureSuccessfulGenesisResponse() {
    final TimeProvider timeProvider = new SystemTimeProvider();
    final SpecConfig config = spec.getGenesisSpec().getConfig();
    final UInt64 genesisTime =
        timeProvider
            .getTimeInSeconds()
            .minus(1020L * config.getSecondsPerSlot() * config.getSlotsPerEpoch());

    final String testHead =
        String.format(
            "{ \"data\": {\"genesis_time\": \"%s\","
                + "\"genesis_validators_root\": \"0xf03f804ff1c97ada13050eb617e66e88e1199c2ce1be0b6b27e36fafb8d3ee48\","
                + "\"genesis_fork_version\": \"0x00004105\"}}",
            genesisTime);

    server
        .when(request().withPath("/eth/v1/beacon/genesis"))
        .respond(response().withStatusCode(200).withBody(testHead));
  }

  private void configureSuccessfulValidatorResponses() throws IOException {
    final String keyManagerValidator1 = validatorResourceFile("8b0f19f_response.json");
    final String keyManagerValidator2 = validatorResourceFile("82c2a92_response.json");
    final String validator1 = validatorResourceFile("a756543_response.json");
    final String validator2 = validatorResourceFile("aa51616_response.json");

    final String validators = validatorResourceFile("validators_response.json");

    setupValidatorStatusResponse(keyManagerPubKey1, keyManagerValidator1);
    setupValidatorStatusResponse(keyManagerPubKey2, keyManagerValidator2);
    setupValidatorStatusResponse(validatorPubKey1, validator1);
    setupValidatorStatusResponse(validatorPubKey2, validator2);

    server
        .when(
            request()
                .withMethod("POST")
                .withPath("/eth/v1/beacon/states/head/validators")
                .withBody(
                    "{\"ids\":["
                        + String.join(
                            ",",
                            quoted(validatorPubKey1),
                            quoted(keyManagerPubKey1),
                            quoted(keyManagerPubKey2),
                            quoted(validatorPubKey2))
                        + "]}"))
        .respond(response().withStatusCode(200).withBody(validators));
  }

  private void setupValidatorStatusResponse(final String publicKey, final String responseString) {
    server
        .when(
            request()
                .withMethod("POST")
                .withPath("/eth/v1/beacon/states/head/validators")
                .withBody("{\"ids\":[" + "\"" + publicKey + "\"" + "]}"))
        .respond(response().withStatusCode(200).withBody(responseString));
  }

  private String validatorResourceFile(final String validatorStatusFile) throws IOException {
    return Resources.toString(
        Resources.getResource(
            "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-responses/"
                + validatorStatusFile),
        UTF_8);
  }

  private void configureSuccessfulVoluntaryExitResponse() {
    server
        .when(request().withPath("/eth/v1/beacon/pool/voluntary_exits"))
        .respond(response().withStatusCode(200));
  }

  private Supplier<List<SignedVoluntaryExit>>
      configureSuccessfulVoluntaryExitResponseWithCapture() {
    final List<String> responses = new ArrayList<>();
    server
        .when(request().withPath("/eth/v1/beacon/pool/voluntary_exits"))
        .respond(
            httpRequest -> {
              responses.add(httpRequest.getBody().toString());
              return response().withStatusCode(200);
            });
    return () ->
        responses.stream()
            .map(
                response -> {
                  try {
                    return JsonUtil.parse(
                        response, SignedVoluntaryExit.SSZ_SCHEMA.getJsonTypeDefinition());
                  } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toList();
  }

  private void configureExternalSignerUpcheck() {
    server
        .when(request().withMethod("GET").withPath("/upcheck"))
        .respond(response().withStatusCode(200));
  }

  private void configureExternalSignerResponse(
      final String validatorPubKey2, final BLSSignature blsSignature) {
    server
        .when(request().withMethod("POST").withPath("/api/v1/eth2/sign/" + validatorPubKey2))
        .respond(response().withStatusCode(200).withBody(blsSignature.toString()));
  }

  private void configureExternalSignerPublicKeys(final List<String> keys) {
    final String body =
        "{["
            + keys.stream()
                .map(
                    k ->
                        "{"
                            + quotedFieldName("validating_pubkey")
                            + quoted(k)
                            + ","
                            + quotedFieldName("derivation_path")
                            + quoted("")
                            + ","
                            + quotedFieldName("readonly")
                            + "false}")
                .collect(Collectors.joining(","))
            + "]}";
    server
        .when(request().withMethod("GET").withPath("/eth/v1/keystores"))
        .respond(response().withStatusCode(200).withBody(body));
  }

  private void configureRejectedVoluntaryExitResponse() throws IOException {
    final String rejectedExitResponseBody =
        Resources.toString(
            Resources.getResource(
                "tech/pegasys/teku/cli/subcommand/voluntary-exit/rejected-exit.json"),
            UTF_8);

    server
        .when(request().withMethod("POST").withPath("/eth/v1/beacon/pool/voluntary_exits"))
        .respond(response().withStatusCode(400).withBody(rejectedExitResponseBody));
  }

  private String quoted(final String unquotedString) {
    return "\"" + unquotedString + "\"";
  }

  private String quotedFieldName(final String unquotedString) {
    return "\"" + unquotedString + "\":";
  }

  private String getTestSpecJsonString(final Spec spec) throws JsonProcessingException {
    return JsonUtil.serialize(new ConfigProvider(spec).getConfig(), GET_SPEC_RESPONSE_TYPE);
  }

  private String extractValidatorId(final String pubKey) {
    return pubKey.substring(2, 9);
  }

  private void assertValidatorsExited(final String... args) {
    Arrays.stream(args)
        .forEach(
            arg -> {
              assertThat(stdOut.toString(UTF_8))
                  .contains(String.format(exitSubmitted, extractValidatorId(arg)));
            });
  }

  private void assertValidatorsNotExited(final String... args) {
    Arrays.stream(args)
        .forEach(
            arg -> {
              assertThat(stdOut.toString(UTF_8))
                  .doesNotContain(String.format(exitSubmitted, extractValidatorId(arg)));
            });
  }

  private void verifyVoluntaryExitSignature(
      final SignedVoluntaryExit signedVoluntaryExit,
      final BLSPublicKey pubKey,
      final Spec spec,
      final SpecMilestone expectedMilestoneDomain) {
    // from configureSuccessfulGenesisResponse
    final Bytes32 genesisValidatorsRoot =
        Bytes32.fromHexString("0xf03f804ff1c97ada13050eb617e66e88e1199c2ce1be0b6b27e36fafb8d3ee48");
    // using base functions next, milestone doesn't matter
    final MiscHelpers miscHelpers = spec.forMilestone(SpecMilestone.BELLATRIX).miscHelpers();
    final Bytes32 domain =
        miscHelpers.computeDomain(
            Domain.VOLUNTARY_EXIT,
            spec.getForkSchedule().getFork(expectedMilestoneDomain).getCurrentVersion(),
            genesisValidatorsRoot);
    final Bytes signingRoot =
        miscHelpers.computeSigningRoot(signedVoluntaryExit.getMessage(), domain);
    assertThat(
            BLSSignatureVerifier.SIMPLE.verify(
                pubKey, signingRoot, signedVoluntaryExit.getSignature()))
        .isTrue();
  }
}
