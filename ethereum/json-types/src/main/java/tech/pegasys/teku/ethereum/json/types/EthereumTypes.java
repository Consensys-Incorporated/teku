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

package tech.pegasys.teku.ethereum.json.types;

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_BUILDER_URL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_INCLUDE_PAYLOAD;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.types.BooleanHeaderTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.EnumHeaderTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.EnumTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringBasedHeaderTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.OctetStreamResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ExecutionPayloadAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

public class EthereumTypes {

  public static final StringValueTypeDefinition<BLSSignature> SIGNATURE_TYPE =
      DeserializableTypeDefinition.string(BLSSignature.class)
          .formatter(BLSSignature::toString)
          .parser(value -> BLSSignature.fromBytesCompressed(Bytes.fromHexString(value)))
          .example(
              "0x1b66ac1fb663c9bc59509846d6ec05345bd908eda73e670af888da41af171505"
                  + "cc411d61252fb6cb3fa0017b679f8bb2305b26a285fa2737f175668d0dff91cc"
                  + "1b66ac1fb663c9bc59509846d6ec05345bd908eda73e670af888da41af171505")
          .description("`BLSSignature Hex` BLS12-381 signature for the current epoch.")
          .format("byte")
          .build();

  public static final StringValueTypeDefinition<BLSPublicKey> PUBLIC_KEY_TYPE =
      DeserializableTypeDefinition.string(BLSPublicKey.class)
          .formatter(BLSPublicKey::toString)
          .parser(BLSPublicKey::fromHexString)
          .example(
              "0x93247f2209abcacf57b75a51dafae777f9dd38bc7053d1a"
                  + "f526f220a7489a6d3a2753e5f3e8b1cfe39b56f43611df74a")
          .description(
              "`BLSPublicKey Hex` The validator's BLS public key, uniquely identifying them. "
                  + "48-bytes, hex encoded with 0x prefix, case insensitive.")
          .format("string")
          .build();

  public static final StringValueTypeDefinition<KZGCommitment> KZG_COMMITMENT_TYPE =
      DeserializableTypeDefinition.string(KZGCommitment.class)
          .formatter(KZGCommitment::toHexString)
          .parser(KZGCommitment::fromHexString)
          .example(
              "0xb09ce4964278eff81a976fbc552488cb84fc4a102f004c87"
                  + "179cb912f49904d1e785ecaf5d184522a58e9035875440ef")
          .description("KZG Commitment")
          .format("byte")
          .build();

  public static final StringValueTypeDefinition<VersionedHash> VERSIONED_HASH_TYPE =
      DeserializableTypeDefinition.string(VersionedHash.class)
          .formatter(VersionedHash::toHexString)
          .parser(VersionedHash::fromHexString)
          .example("0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2")
          .description("Versioned Hash")
          .format("byte")
          .build();

  public static final StringValueTypeDefinition<SpecMilestone> MILESTONE_TYPE =
      new EnumTypeDefinition<>(
          SpecMilestone.class, milestone -> milestone.name().toLowerCase(Locale.ROOT));

  public static final EnumHeaderTypeDefinition<SpecMilestone> ETH_CONSENSUS_HEADER_TYPE =
      new EnumHeaderTypeDefinition.EnumTypeHeaderDefinitionBuilder<>(
              SpecMilestone.class, milestone -> milestone.name().toLowerCase(Locale.ROOT))
          .title(HEADER_CONSENSUS_VERSION)
          .required(true)
          .description(
              "Required in response so client can deserialize returned json or ssz data more effectively.")
          .example("phase0")
          .build();

  public static final BooleanHeaderTypeDefinition ETH_HEADER_EXECUTION_PAYLOAD_BLINDED_TYPE =
      new BooleanHeaderTypeDefinition(
          HEADER_EXECUTION_PAYLOAD_BLINDED,
          Optional.of(true),
          "Required in response so client can deserialize returned json or ssz data to the correct object.");

  public static final StringBasedHeaderTypeDefinition<UInt256>
      ETH_HEADER_EXECUTION_PAYLOAD_VALUE_TYPE =
          new StringBasedHeaderTypeDefinition.Builder<UInt256>()
              .title(HEADER_EXECUTION_PAYLOAD_VALUE)
              .description(
                  "Execution payload value in Wei. Required in response so client can determine relative value of execution payloads.")
              .formatter(value -> value.toBigInteger().toString(10))
              .parser(value -> UInt256.valueOf(new BigInteger(value, 10)))
              .example("1")
              .required(true)
              .build();

  public static final StringBasedHeaderTypeDefinition<UInt256>
      ETH_HEADER_CONSENSUS_BLOCK_VALUE_TYPE =
          new StringBasedHeaderTypeDefinition.Builder<UInt256>()
              .title(HEADER_CONSENSUS_BLOCK_VALUE)
              .description(
                  "Consensus rewards for this block in Wei paid to the proposer. The rewards value is the sum of values of the proposer rewards from attestations, sync committees and slashings included in the proposal. Required in response so client can determine relative value of consensus blocks.")
              .formatter(value -> value.toBigInteger().toString(10))
              .parser(value -> UInt256.valueOf(new BigInteger(value, 10)))
              .example("1")
              .required(true)
              .build();

  public static final BooleanHeaderTypeDefinition ETH_HEADER_EXECUTION_PAYLOAD_INCLUDED_TYPE =
      new BooleanHeaderTypeDefinition(
          HEADER_INCLUDE_PAYLOAD,
          Optional.of(true),
          """
                          Indicates whether the execution payload envelope is included in the response.
                          When `true`, the `data` field contains the full
                          execution payload envelope, blobs, and KZG proofs. When `false`, the `data`
                          field contains only a `BeaconBlock`.""");

  public static final StringBasedHeaderTypeDefinition<String> ETH_HEADER_BUILDER_URL_TYPE =
      new StringBasedHeaderTypeDefinition.Builder<String>()
          .title(HEADER_BUILDER_URL)
          .description(
              """
                The url of a winning builder when the bid was retrieved via the builder-API.
                The validator client MUST echo this value in the Eth-Builder-Url request header when publishing the signed block,
                so the beacon node can forward the block to the same builder via submitSignedBeaconBlock. Absent for a self-built block or a bid won over p2p.""")
          .formatter(Function.identity())
          .parser(Function.identity())
          .build();

  public static <X extends SszData, T extends ObjectAndMetaData<X>>
      ResponseContentTypeDefinition<? extends T> sszResponseType() {
    return new OctetStreamResponseContentTypeDefinition<>(
        (data, out) -> data.getData().sszSerialize(out),
        value -> getSszHeaders(__ -> value.getMilestone(), value.getData()));
  }

  public static ResponseContentTypeDefinition<BlockContainerAndMetaData>
      blockContainerAndMetaDataSszResponseType() {
    return new OctetStreamResponseContentTypeDefinition<>(
        (data, out) -> data.blockContainer().sszSerialize(out),
        value -> getSszHeaders(__ -> value.milestone(), value.blockContainer()));
  }

  public static ResponseContentTypeDefinition<ExecutionPayloadAndMetaData>
      executionPayloadAndMetaDataSszResponseType() {
    return new OctetStreamResponseContentTypeDefinition<>(
        (value, out) -> value.data().sszSerialize(out),
        value -> getSszHeaders(__ -> value.milestone(), value.data()));
  }

  public static <T extends SszData> ResponseContentTypeDefinition<T> sszResponseType(
      final Function<T, SpecMilestone> milestoneSelector) {
    return new OctetStreamResponseContentTypeDefinition<>(
        SszData::sszSerialize, value -> getSszHeaders(milestoneSelector, value));
  }

  @NotNull
  private static <T extends SszData> Map<String, String> getSszHeaders(
      final Function<T, SpecMilestone> milestoneSelector, final T value) {
    return Map.of(
        HEADER_CONSENSUS_VERSION,
        milestoneSelector.apply(value).lowerCaseName(),
        RestApiConstants.HEADER_CONTENT_DISPOSITION,
        getSszFilename(value));
  }

  private static <T extends SszData> String getSszFilename(final T value) {
    return String.format(
        "filename=\"%s%s.ssz\"",
        value.getSchema().getName().map(name -> name + "-").orElse(""),
        value.hashTreeRoot().toUnprefixedHexString());
  }
}
