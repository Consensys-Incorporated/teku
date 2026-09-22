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

package tech.pegasys.teku.data.slashinginterchange;

import static tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord.isNeverSigned;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record SigningHistory(
    BLSPublicKey pubkey,
    List<SignedBlock> signedBlocks,
    List<SignedAttestation> signedAttestations) {

  public static final DeserializableTypeDefinition<BLSPublicKey> PUBKEY_TYPE =
      DeserializableTypeDefinition.string(BLSPublicKey.class)
          .formatter(BLSPublicKey::toHexString)
          .parser(BLSPublicKey::fromHexString)
          .build();

  public static SigningHistory createSigningHistory(
      final BLSPublicKey pubkey, final ValidatorSigningRecord record) {
    final List<SignedBlock> blocks = new ArrayList<>();
    final List<SignedAttestation> attestations = new ArrayList<>();
    blocks.add(new SignedBlock(record.blockSlot(), Optional.empty()));
    if (!isNeverSigned(record.attestationSourceEpoch())
        || !isNeverSigned(record.attestationTargetEpoch())) {
      attestations.add(
          new SignedAttestation(
              record.attestationSourceEpoch(), record.attestationTargetEpoch(), Optional.empty()));
    }
    return new SigningHistory(pubkey, blocks, attestations);
  }

  public static DeserializableTypeDefinition<SigningHistory> getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(SigningHistory.class, SigningHistoryBuilder.class)
        .initializer(SigningHistoryBuilder::new)
        .finisher(SigningHistoryBuilder::build)
        .withField("pubkey", PUBKEY_TYPE, SigningHistory::pubkey, SigningHistoryBuilder::pubkey)
        .withField(
            "signed_blocks",
            DeserializableTypeDefinition.listOf(SignedBlock.getJsonTypeDefinition()),
            SigningHistory::signedBlocks,
            SigningHistoryBuilder::signedBlocks)
        .withField(
            "signed_attestations",
            DeserializableTypeDefinition.listOf(SignedAttestation.getJsonTypeDefinition()),
            SigningHistory::signedAttestations,
            SigningHistoryBuilder::signedAttestations)
        .build();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("pubkey", pubkey)
        .add("signedBlocks", signedBlocks)
        .add("signedAttestations", signedAttestations)
        .toString();
  }

  public ValidatorSigningRecord toValidatorSigningRecord(
      final Optional<ValidatorSigningRecord> maybeRecord, final Bytes32 genesisValidatorsRoot) {

    final UInt64 lastSignedBlockSlot =
        signedBlocks.stream()
            .map(SignedBlock::slot)
            .filter(Objects::nonNull)
            .max(UInt64::compareTo)
            .orElse(UInt64.ZERO);
    final UInt64 lastSignedAttestationSourceEpoch =
        signedAttestations.stream()
            .map(SignedAttestation::sourceEpoch)
            .filter(Objects::nonNull)
            .filter(epoch -> !epoch.equals(UInt64.MAX_VALUE))
            .max(UInt64::compareTo)
            .orElse(null);
    final UInt64 lastSignedAttestationTargetEpoch =
        signedAttestations.stream()
            .map(SignedAttestation::targetEpoch)
            .filter(Objects::nonNull)
            .filter(epoch -> !epoch.equals(UInt64.MAX_VALUE))
            .max(UInt64::compareTo)
            .orElse(null);

    if (maybeRecord.isPresent()) {
      final ValidatorSigningRecord record = maybeRecord.get();
      return new ValidatorSigningRecord(
          Optional.ofNullable(genesisValidatorsRoot),
          record.blockSlot().max(lastSignedBlockSlot),
          nullSafeMax(record.attestationSourceEpoch(), lastSignedAttestationSourceEpoch),
          nullSafeMax(record.attestationTargetEpoch(), lastSignedAttestationTargetEpoch));
    }
    return new ValidatorSigningRecord(
        Optional.ofNullable(genesisValidatorsRoot),
        lastSignedBlockSlot,
        lastSignedAttestationSourceEpoch,
        lastSignedAttestationTargetEpoch);
  }

  private UInt64 nullSafeMax(final UInt64 a, final UInt64 b) {
    if (a == null) {
      return b;
    } else if (b == null) {
      return a;
    }
    return a.max(b);
  }

  static class SigningHistoryBuilder {
    BLSPublicKey pubkey;
    List<SignedBlock> signedBlocks;
    List<SignedAttestation> signedAttestations;

    SigningHistoryBuilder pubkey(final BLSPublicKey pubkey) {
      this.pubkey = pubkey;
      return this;
    }

    SigningHistoryBuilder signedBlocks(final List<SignedBlock> signedBlocks) {
      this.signedBlocks = signedBlocks;
      return this;
    }

    SigningHistoryBuilder signedAttestations(final List<SignedAttestation> signedAttestations) {
      this.signedAttestations = signedAttestations;
      return this;
    }

    SigningHistory build() {
      return new SigningHistory(pubkey, signedBlocks, signedAttestations);
    }
  }
}
