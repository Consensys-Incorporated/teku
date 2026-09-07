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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody;

import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodySchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodySchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BlindedBeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodySchemaDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBeaconBlockBodySchemaDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodySchemaElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BlindedBeaconBlockBodySchemaElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodySchemaGloas;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface BeaconBlockBodySchema<T extends BeaconBlockBody> extends SszContainerSchema<T> {
  SafeFuture<? extends BeaconBlockBody> createBlockBody(
      Function<BeaconBlockBodyBuilder, SafeFuture<Void>> bodyBuilder);

  BeaconBlockBody createEmpty();

  /**
   * Creates the body of the genesis block for the supplied genesis state.
   *
   * <p>Up to Fulu, {@code initialize_beacon_state_from_eth1} defines the genesis block body as an
   * empty {@code BeaconBlockBody}. Forks whose genesis block body embeds data from the genesis
   * state (Gloas) override this.
   */
  default BeaconBlockBody createGenesisBody(final BeaconState genesisState) {
    return createEmpty();
  }

  @Override
  T createFromBackingNode(TreeNode node);

  SszListSchema<ProposerSlashing, ?> getProposerSlashingsSchema();

  SszListSchema<AttesterSlashing, ?> getAttesterSlashingsSchema();

  SszListSchema<Attestation, ?> getAttestationsSchema();

  SszListSchema<Deposit, ?> getDepositsSchema();

  SszListSchema<SignedVoluntaryExit, ?> getVoluntaryExitsSchema();

  default Optional<BeaconBlockBodySchemaAltair<?>> toVersionAltair() {
    return Optional.empty();
  }

  default Optional<BeaconBlockBodySchemaBellatrix<?>> toVersionBellatrix() {
    return Optional.empty();
  }

  default Optional<BeaconBlockBodySchemaCapella<?>> toVersionCapella() {
    return Optional.empty();
  }

  default Optional<BeaconBlockBodySchemaDeneb<?>> toVersionDeneb() {
    return Optional.empty();
  }

  default Optional<BeaconBlockBodySchemaElectra<?>> toVersionElectra() {
    return Optional.empty();
  }

  default Optional<BeaconBlockBodySchemaGloas<?>> toVersionGloas() {
    return Optional.empty();
  }

  default Optional<BlindedBeaconBlockBodySchemaBellatrix<?>> toBlindedVersionBellatrix() {
    return Optional.empty();
  }

  default Optional<BlindedBeaconBlockBodySchemaCapella<?>> toBlindedVersionCapella() {
    return Optional.empty();
  }

  default Optional<BlindedBeaconBlockBodySchemaDeneb<?>> toBlindedVersionDeneb() {
    return Optional.empty();
  }

  default Optional<BlindedBeaconBlockBodySchemaElectra<?>> toBlindedVersionElectra() {
    return Optional.empty();
  }

  /**
   * getBlindedNodeGeneralizedIndices
   *
   * @return a list of generalized indices in numeric order
   */
  LongList getBlindedNodeGeneralizedIndices();
}
