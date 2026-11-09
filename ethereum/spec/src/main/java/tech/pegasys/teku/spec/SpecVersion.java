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

package tech.pegasys.teku.spec;

import static tech.pegasys.teku.infrastructure.time.SystemTimeProvider.SYSTEM_TIME_PROVIDER;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.logic.DelegatingSpecLogic;
import tech.pegasys.teku.spec.logic.SpecLogic;
import tech.pegasys.teku.spec.logic.versions.altair.SpecLogicAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.SpecLogicBellatrix;
import tech.pegasys.teku.spec.logic.versions.capella.SpecLogicCapella;
import tech.pegasys.teku.spec.logic.versions.deneb.SpecLogicDeneb;
import tech.pegasys.teku.spec.logic.versions.electra.SpecLogicElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.SpecLogicFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.SpecLogicGloas;
import tech.pegasys.teku.spec.logic.versions.heze.SpecLogicHeze;
import tech.pegasys.teku.spec.logic.versions.phase0.SpecLogicPhase0;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPhase0;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistryBuilder;

public class SpecVersion extends DelegatingSpecLogic {
  private final SpecMilestone milestone;
  private final SpecConfig config;
  private final SchemaDefinitions schemaDefinitions;

  private SpecVersion(
      final SpecMilestone milestone,
      final SpecConfig config,
      final SchemaDefinitions schemaDefinitions,
      final SpecLogic specLogic) {
    super(specLogic);
    this.milestone = milestone;
    this.config = config;
    this.schemaDefinitions = schemaDefinitions;
  }

  public static Optional<SpecVersion> create(
      final SpecMilestone milestone,
      final SpecConfig specConfig,
      final SchemaRegistryBuilder schemaRegistryBuilder) {

    return switch (milestone) {
      case PHASE0 -> Optional.of(createPhase0(specConfig, schemaRegistryBuilder));
      case ALTAIR ->
          specConfig.toVersionAltair().map(config -> createAltair(config, schemaRegistryBuilder));
      case BELLATRIX ->
          specConfig
              .toVersionBellatrix()
              .map(config -> createBellatrix(config, schemaRegistryBuilder));
      case CAPELLA ->
          specConfig.toVersionCapella().map(config -> createCapella(config, schemaRegistryBuilder));
      case DENEB ->
          specConfig.toVersionDeneb().map(config -> createDeneb(config, schemaRegistryBuilder));
      case ELECTRA ->
          specConfig.toVersionElectra().map(config -> createElectra(config, schemaRegistryBuilder));
      case FULU ->
          specConfig.toVersionFulu().map(config -> createFulu(config, schemaRegistryBuilder));
      case GLOAS ->
          specConfig.toVersionGloas().map(config -> createGloas(config, schemaRegistryBuilder));
      case HEZE ->
          specConfig.toVersionHeze().map(config -> createHeze(config, schemaRegistryBuilder));
    };
  }

  static SpecVersion createPhase0(
      final SpecConfig specConfig, final SchemaRegistryBuilder schemaRegistryBuilder) {
    final SchemaRegistry schemaRegistry =
        schemaRegistryBuilder.build(SpecMilestone.PHASE0, specConfig);
    final SchemaDefinitions schemaDefinitions = new SchemaDefinitionsPhase0(schemaRegistry);
    final SpecLogic specLogic =
        SpecLogicPhase0.create(specConfig, schemaDefinitions, SYSTEM_TIME_PROVIDER);
    return new SpecVersion(SpecMilestone.PHASE0, specConfig, schemaDefinitions, specLogic);
  }

  static SpecVersion createAltair(
      final SpecConfigAltair specConfig, final SchemaRegistryBuilder schemaRegistryBuilder) {
    final SchemaRegistry schemaRegistry =
        schemaRegistryBuilder.build(SpecMilestone.ALTAIR, specConfig);
    final SchemaDefinitionsAltair schemaDefinitions = new SchemaDefinitionsAltair(schemaRegistry);
    final SpecLogic specLogic =
        SpecLogicAltair.create(specConfig, schemaDefinitions, SYSTEM_TIME_PROVIDER);
    return new SpecVersion(SpecMilestone.ALTAIR, specConfig, schemaDefinitions, specLogic);
  }

  static SpecVersion createBellatrix(
      final SpecConfigBellatrix specConfig, final SchemaRegistryBuilder schemaRegistryBuilder) {
    final SchemaRegistry schemaRegistry =
        schemaRegistryBuilder.build(SpecMilestone.BELLATRIX, specConfig);
    final SchemaDefinitionsBellatrix schemaDefinitions =
        new SchemaDefinitionsBellatrix(schemaRegistry);
    final SpecLogic specLogic =
        SpecLogicBellatrix.create(specConfig, schemaDefinitions, SYSTEM_TIME_PROVIDER);
    return new SpecVersion(SpecMilestone.BELLATRIX, specConfig, schemaDefinitions, specLogic);
  }

  static SpecVersion createCapella(
      final SpecConfigCapella specConfig, final SchemaRegistryBuilder schemaRegistryBuilder) {
    final SchemaRegistry schemaRegistry =
        schemaRegistryBuilder.build(SpecMilestone.CAPELLA, specConfig);
    final SchemaDefinitionsCapella schemaDefinitions = new SchemaDefinitionsCapella(schemaRegistry);
    final SpecLogicCapella specLogic =
        SpecLogicCapella.create(specConfig, schemaDefinitions, SYSTEM_TIME_PROVIDER);
    return new SpecVersion(SpecMilestone.CAPELLA, specConfig, schemaDefinitions, specLogic);
  }

  static SpecVersion createDeneb(
      final SpecConfigDeneb specConfig, final SchemaRegistryBuilder schemaRegistryBuilder) {
    final SchemaRegistry schemaRegistry =
        schemaRegistryBuilder.build(SpecMilestone.DENEB, specConfig);
    final SchemaDefinitionsDeneb schemaDefinitions = new SchemaDefinitionsDeneb(schemaRegistry);
    final SpecLogicDeneb specLogic =
        SpecLogicDeneb.create(specConfig, schemaDefinitions, SYSTEM_TIME_PROVIDER);
    return new SpecVersion(SpecMilestone.DENEB, specConfig, schemaDefinitions, specLogic);
  }

  static SpecVersion createElectra(
      final SpecConfigElectra specConfig, final SchemaRegistryBuilder schemaRegistryBuilder) {
    final SchemaRegistry schemaRegistry =
        schemaRegistryBuilder.build(SpecMilestone.ELECTRA, specConfig);
    final SchemaDefinitionsElectra schemaDefinitions = new SchemaDefinitionsElectra(schemaRegistry);
    final SpecLogicElectra specLogic =
        SpecLogicElectra.create(specConfig, schemaDefinitions, SYSTEM_TIME_PROVIDER);
    return new SpecVersion(SpecMilestone.ELECTRA, specConfig, schemaDefinitions, specLogic);
  }

  static SpecVersion createFulu(
      final SpecConfigFulu specConfig, final SchemaRegistryBuilder schemaRegistryBuilder) {
    final SchemaRegistry schemaRegistry =
        schemaRegistryBuilder.build(SpecMilestone.FULU, specConfig);
    final SchemaDefinitionsFulu schemaDefinitions = new SchemaDefinitionsFulu(schemaRegistry);
    final SpecLogicFulu specLogic =
        SpecLogicFulu.create(specConfig, schemaDefinitions, SYSTEM_TIME_PROVIDER);
    return new SpecVersion(SpecMilestone.FULU, specConfig, schemaDefinitions, specLogic);
  }

  static SpecVersion createGloas(
      final SpecConfigGloas specConfig, final SchemaRegistryBuilder schemaRegistryBuilder) {
    final SchemaRegistry schemaRegistry =
        schemaRegistryBuilder.build(SpecMilestone.GLOAS, specConfig);
    final SchemaDefinitionsGloas schemaDefinitions = new SchemaDefinitionsGloas(schemaRegistry);
    final SpecLogicGloas specLogic =
        SpecLogicGloas.create(specConfig, schemaDefinitions, SYSTEM_TIME_PROVIDER);
    return new SpecVersion(SpecMilestone.GLOAS, specConfig, schemaDefinitions, specLogic);
  }

  static SpecVersion createHeze(
      final SpecConfigHeze specConfig, final SchemaRegistryBuilder schemaRegistryBuilder) {
    final SchemaRegistry schemaRegistry =
        schemaRegistryBuilder.build(SpecMilestone.HEZE, specConfig);
    final SchemaDefinitionsHeze schemaDefinitions = new SchemaDefinitionsHeze(schemaRegistry);
    final SpecLogicHeze specLogic =
        SpecLogicHeze.create(specConfig, schemaDefinitions, SYSTEM_TIME_PROVIDER);
    return new SpecVersion(SpecMilestone.HEZE, specConfig, schemaDefinitions, specLogic);
  }

  public SpecMilestone getMilestone() {
    return milestone;
  }

  public SpecConfig getConfig() {
    return config;
  }

  public int getSlotsPerEpoch() {
    return config.getSlotsPerEpoch();
  }

  public int getSlotsPerHistoricalRoot() {
    return config.getSlotsPerHistoricalRoot();
  }

  public SchemaDefinitions getSchemaDefinitions() {
    return schemaDefinitions;
  }
}
