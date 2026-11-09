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

package tech.pegasys.teku.test.acceptance.dsl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class GenesisGenerator {

  private final SystemTimeProvider timeProvider = new SystemTimeProvider();
  private String network;
  private ValidatorKeystores validatorKeys;
  private Optional<Function<Spec, ExecutionPayloadHeader>> genesisExecutionPayloadHeaderSource =
      Optional.empty();
  private int genesisDelaySeconds = 10;
  private Optional<Integer> genesisTime = Optional.empty();
  private Consumer<SpecConfigBuilder> specConfigModifier = builder -> {};

  public GenesisGenerator network(final String network) {
    this.network = network;
    return this;
  }

  public GenesisGenerator withGenesisTime(final Integer genesisTime) {
    this.genesisTime = Optional.of(genesisTime);
    return this;
  }

  public GenesisGenerator withAltairEpoch(final UInt64 altairForkEpoch) {
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder -> specConfigBuilder.altairForkEpoch(altairForkEpoch));
    return this;
  }

  public GenesisGenerator withBellatrixEpoch(final UInt64 bellatrixForkEpoch) {
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder -> specConfigBuilder.bellatrixForkEpoch(bellatrixForkEpoch));
    return this;
  }

  public GenesisGenerator withCapellaEpoch(final UInt64 capellaForkEpoch) {
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder -> specConfigBuilder.capellaForkEpoch(capellaForkEpoch));
    return this;
  }

  public GenesisGenerator withDenebEpoch(final UInt64 denebForkEpoch) {
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder -> specConfigBuilder.denebForkEpoch(denebForkEpoch));
    return this;
  }

  public GenesisGenerator withElectraEpoch(final UInt64 electraForkEpoch) {
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder -> specConfigBuilder.electraForkEpoch(electraForkEpoch));
    return this;
  }

  public GenesisGenerator withFuluEpoch(final UInt64 fuluForkEpoch) {
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder -> specConfigBuilder.fuluForkEpoch(fuluForkEpoch));
    return this;
  }

  public GenesisGenerator witGloasEpoch(final UInt64 gloasForkEpoch) {
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder -> specConfigBuilder.gloasForkEpoch(gloasForkEpoch));
    return this;
  }

  public GenesisGenerator withTotalTerminalDifficulty(final long totalTerminalDifficulty) {
    return withTotalTerminalDifficulty(UInt256.valueOf(totalTerminalDifficulty));
  }

  public GenesisGenerator withTotalTerminalDifficulty(final UInt256 totalTerminalDifficulty) {
    specConfigModifier =
        specConfigModifier.andThen(
            specConfigBuilder ->
                specConfigBuilder.bellatrixBuilder(
                    bellatrixBuilder ->
                        bellatrixBuilder.terminalTotalDifficulty(totalTerminalDifficulty)));
    return this;
  }

  public GenesisGenerator validatorKeys(final ValidatorKeystores... validatorKeys) {
    this.validatorKeys =
        Stream.of(validatorKeys)
            .reduce(new ValidatorKeystores(Collections.emptyList()), ValidatorKeystores::add);
    return this;
  }

  public GenesisGenerator genesisDelaySeconds(final int genesisDelaySeconds) {
    this.genesisDelaySeconds = genesisDelaySeconds;
    return this;
  }

  public GenesisGenerator genesisExecutionPayloadHeaderSource(
      final Function<Spec, ExecutionPayloadHeader> genesisExecutionPayloadHeaderSource) {
    this.genesisExecutionPayloadHeaderSource = Optional.of(genesisExecutionPayloadHeaderSource);
    return this;
  }

  public InitialStateData generate() {
    final Spec spec = SpecFactory.create(network, false, specConfigModifier);
    final GenesisStateBuilder genesisBuilder = new GenesisStateBuilder();
    genesisBuilder
        .spec(spec)
        .genesisTime(
            genesisTime
                .map(UInt64::valueOf)
                .orElse(timeProvider.getTimeInSeconds().plus(genesisDelaySeconds)));
    validatorKeys
        .getValidatorKeys()
        .forEach(
            validator ->
                genesisBuilder.addValidator(
                    validator.getValidatorKey(), validator.getWithdrawalCredentials()));

    genesisExecutionPayloadHeaderSource.ifPresent(
        source -> genesisBuilder.executionPayloadHeader(source.apply(spec)));

    return new InitialStateData(genesisBuilder.build());
  }

  public static class InitialStateData {

    private final BeaconState genesisState;

    public InitialStateData(final BeaconState genesisState) {
      this.genesisState = genesisState;
    }

    public File writeToTempFile() throws IOException {
      final File tempFile = Files.createTempFile("state", ".ssz").toFile();
      tempFile.deleteOnExit();
      Files.write(tempFile.toPath(), genesisState.sszSerialize().toArrayUnsafe());
      return tempFile;
    }
  }
}
