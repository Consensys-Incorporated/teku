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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Streams;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszDataAssert;
import tech.pegasys.teku.infrastructure.ssz.SszOptional;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszImmutableContainer;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszOptionalSchemaTest extends SszSchemaTestBase {

  public static Stream<SszOptionalSchema<?, ?>> testOptionalSchemas() {
    return Stream.of(
        SszOptionalSchema.create(SszPrimitiveSchemas.BIT_SCHEMA),
        SszOptionalSchema.create(SszListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 32)),
        SszOptionalSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA),
        SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA),
        SszOptionalSchema.create(SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)));
  }

  public static Stream<SszSchema<?>> testContainerOptionalSchemas() {
    return Stream.of(ContainerWithOptionals.SSZ_SCHEMA);
  }

  @Test
  void verifySszLengthBounds() {
    assertThat(SszOptionalSchema.create(SszPrimitiveSchemas.BIT_SCHEMA).getSszLengthBounds())
        .matches(bound -> bound.getMinBytes() == 0)
        .matches(bound -> bound.getMaxBytes() == 2)
        .matches(bound -> bound.getMaxBits() == 9);

    assertThat(SszOptionalSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA).getSszLengthBounds())
        .matches(bound -> bound.getMinBytes() == 0)
        .matches(bound -> bound.getMaxBytes() == 33);
  }

  @Override
  public Stream<? extends SszSchema<?>> testSchemas() {
    return Streams.concat(testOptionalSchemas(), testContainerOptionalSchemas());
  }

  @ParameterizedTest(name = "schema={0}, value={1}")
  @MethodSource("getOptionalSchemaFixtures")
  public <T extends SszData> void testFixtures(
      final SszOptionalSchema<T, ? extends SszOptional<?>> schema,
      final Optional<T> value,
      final String serialized,
      final String hashTreeRoot) {
    final SszOptional<T> actualValue = schema.createFromValue(value);
    assertThat(actualValue.getValue()).isEqualTo(value);
    final Bytes serializedExpected = Bytes.fromHexString(serialized);
    assertThat(actualValue.sszSerialize()).isEqualTo(serializedExpected);
    assertThat(actualValue.hashTreeRoot()).isEqualTo(Bytes.fromHexString(hashTreeRoot));
    final SszOptional<T> expectedValue = schema.sszDeserialize(serializedExpected);
    assertThat(expectedValue).isEqualTo(actualValue);
    SszDataAssert.assertThatSszData(actualValue).isEqualByAllMeansTo(expectedValue);
  }

  static Stream<Arguments> getOptionalSchemaFixtures() {
    return Stream.of(
        Arguments.of(
            SszOptionalSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA),
            Optional.of(SszByte.of(8)),
            "0108",
            "70d1aed87df4080b5ddec57fba8210e62ca447337ad44c33fa6e4e5cd2817469"),
        Arguments.of(
            SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA),
            Optional.of(SszUInt64.of(UInt64.valueOf(64))),
            "014000000000000000",
            "6dcad412e5386241d11ee3a5ba5be6ddc10cdcc9f0e36ec206de7b4635fa6ccb"),
        Arguments.of(
            SszOptionalSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA),
            Optional.of(SszUInt256.of(UInt256.valueOf(256))),
            "010001000000000000000000000000000000000000000000000000000000000000",
            "57d968b4447c19d4c1c84747aa45b0aa4c187b9397c6f1b513fb31f68f943c9d"),
        Arguments.of(
            SszOptionalSchema.create(SszPrimitiveSchemas.BIT_SCHEMA),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(SszPrimitiveSchemas.BIT_SCHEMA),
            Optional.of(SszBit.of(true)),
            "0101",
            "56d8a66fbae0300efba7ec2c531973aaae22e7a2ed6ded081b5b32d07a32780a"),
        Arguments.of(
            SszOptionalSchema.create(SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)),
            Optional.of(
                SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)
                    .createFromValue(Optional.empty())),
            "01",
            "e832d263aaa8f9417d9f45a702834f6961ee7b15ad4d3d27f2b0f4fe79d33031"),
        Arguments.of(
            SszOptionalSchema.create(SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)),
            Optional.of(
                SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)
                    .createFromValue(Optional.of(SszUInt64.of(UInt64.valueOf(64))))),
            "01014000000000000000",
            "ff3f9699283db0b019849434dd0fc77a02e8a4bc5b88f8984fa5cee66effcd0d"),
        Arguments.of(
            SszOptionalSchema.create(SszVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 1)),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(SszVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 1)),
            Optional.of(
                SszVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 1)
                    .createFromElements(List.of(SszUInt64.of(UInt64.valueOf(64))))),
            "014000000000000000",
            "6dcad412e5386241d11ee3a5ba5be6ddc10cdcc9f0e36ec206de7b4635fa6ccb"),
        Arguments.of(
            SszOptionalSchema.create(SszVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 5)),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(SszVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 5)),
            Optional.of(
                SszVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 5)
                    .createFromElements(
                        List.of(
                            SszUInt64.of(UInt64.valueOf(64)),
                            SszUInt64.of(UInt64.valueOf(64)),
                            SszUInt64.of(UInt64.valueOf(64)),
                            SszUInt64.of(UInt64.valueOf(64)),
                            SszUInt64.of(UInt64.valueOf(64))))),
            "0140000000000000004000000000000000400000000000000040000000000000004000000000000000",
            "7814c91b028072cca5d06e0e6178493449d68ee51d69bdd8033a6b6205ac5db6"),
        Arguments.of(
            SszOptionalSchema.create(SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 1)),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 1)),
            Optional.of(
                SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 1)
                    .createFromElements(List.of())),
            "01",
            "e832d263aaa8f9417d9f45a702834f6961ee7b15ad4d3d27f2b0f4fe79d33031"),
        Arguments.of(
            SszOptionalSchema.create(SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 1)),
            Optional.of(
                SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 1)
                    .createFromElements(List.of(SszUInt64.of(UInt64.valueOf(64))))),
            "014000000000000000",
            "ff3f9699283db0b019849434dd0fc77a02e8a4bc5b88f8984fa5cee66effcd0d"),
        Arguments.of(
            SszOptionalSchema.create(SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 5)),
            Optional.of(
                SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 5)
                    .createFromElements(
                        List.of(
                            SszUInt64.of(UInt64.valueOf(64)), SszUInt64.of(UInt64.valueOf(64))))),
            "0140000000000000004000000000000000",
            "3a52e16bd855712f540466add35adc98b871705b4a844ecdb015eaaaf199a6b3"),
        Arguments.of(
            SszOptionalSchema.create(
                SszListSchema.create(
                    SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA), 9)),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(
                SszListSchema.create(
                    SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA), 9)),
            Optional.of(
                SszListSchema.create(SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA), 9)
                    .createFromElements(
                        List.of(
                            SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)
                                .createFromValue(Optional.empty()),
                            SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)
                                .createFromValue(Optional.of(SszUInt64.of(UInt64.valueOf(64))))))),
            "010800000008000000014000000000000000",
            "d4148e7727295cd1780f845b2d040c683ce9701e8242d8fe076e6b46eb8e27f8"),
        Arguments.of(
            SszOptionalSchema.create(Foo.SSZ_SCHEMA),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(Foo.SSZ_SCHEMA),
            Optional.of(
                Foo.SSZ_SCHEMA.createFromFieldValues(
                    List.of(
                        SszUInt64.of(UInt64.valueOf(64)),
                        SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)
                            .createFromValue(Optional.empty()),
                        SszOptionalSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA)
                            .createFromValue(Optional.empty())))),
            "0140000000000000001000000010000000",
            "55229e707d43a5fb54a35d1a5bcbc9e5cb8dc38020865ec3278ce313944fcba9"),
        Arguments.of(
            SszOptionalSchema.create(Foo.SSZ_SCHEMA),
            Optional.of(
                Foo.SSZ_SCHEMA.createFromFieldValues(
                    List.of(
                        SszUInt64.of(UInt64.valueOf(64)),
                        SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)
                            .createFromValue(Optional.of(SszUInt64.of(UInt64.valueOf(32)))),
                        SszOptionalSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA)
                            .createFromValue(Optional.empty())))),
            "0140000000000000001000000019000000012000000000000000",
            "d015a68cbdffec2578b1ed161bb7bef4ca6e3cbbeb50ed491ca4fd3158a69be7"),
        Arguments.of(
            SszOptionalSchema.create(Foo.SSZ_SCHEMA),
            Optional.of(
                Foo.SSZ_SCHEMA.createFromFieldValues(
                    List.of(
                        SszUInt64.of(UInt64.valueOf(64)),
                        SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)
                            .createFromValue(Optional.of(SszUInt64.of(UInt64.valueOf(32)))),
                        SszOptionalSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA)
                            .createFromValue(Optional.of(SszUInt256.of(UInt256.valueOf(16))))))),
            "0140000000000000001000000019000000012000000000000000011000000000000000000000000000000000000000000000000000000000000000",
            "ac98bccb9160febc633bb7216703dc9b5a9da7429d92989b4caaa0f962027391"),
        Arguments.of(
            SszOptionalSchema.create(SszListSchema.create(Foo.SSZ_SCHEMA, 1)),
            Optional.of(
                SszListSchema.create(Foo.SSZ_SCHEMA, 1)
                    .createFromElements(
                        List.of(
                            Foo.SSZ_SCHEMA.createFromFieldValues(
                                List.of(
                                    SszUInt64.of(UInt64.valueOf(64)),
                                    SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)
                                        .createFromValue(
                                            Optional.of(SszUInt64.of(UInt64.valueOf(32)))),
                                    SszOptionalSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA)
                                        .createFromValue(Optional.empty())))))),
            "010400000040000000000000001000000019000000012000000000000000",
            "b1c9e192b5b13b2aa6cad8e8edd2a994a1ba46005816ae0361bb826bf37cbdcf"),
        Arguments.of(
            SszOptionalSchema.create(
                SszListSchema.create(SszOptionalSchema.create(Foo.SSZ_SCHEMA), 1)),
            Optional.of(
                SszListSchema.create(SszOptionalSchema.create(Foo.SSZ_SCHEMA), 1)
                    .createFromElements(
                        List.of(
                            SszOptionalSchema.create(Foo.SSZ_SCHEMA)
                                .createFromValue(
                                    Optional.of(
                                        Foo.SSZ_SCHEMA.createFromFieldValues(
                                            List.of(
                                                SszUInt64.of(UInt64.valueOf(64)),
                                                SszOptionalSchema.create(
                                                        SszPrimitiveSchemas.UINT64_SCHEMA)
                                                    .createFromValue(
                                                        Optional.of(
                                                            SszUInt64.of(UInt64.valueOf(32)))),
                                                SszOptionalSchema.create(
                                                        SszPrimitiveSchemas.UINT256_SCHEMA)
                                                    .createFromValue(
                                                        Optional.of(
                                                            SszUInt256.of(
                                                                UInt256.valueOf(16))))))))))),
            "01040000000140000000000000001000000019000000012000000000000000011000000000000000000000000000000000000000000000000000000000000000",
            "38f5d8a77ab4e1f1be4472802cfd57332f7040e6dcff1ee1eb7c6d1f30e3e690"),
        Arguments.of(
            SszOptionalSchema.create(SszBitvectorSchema.create(1)),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(SszBitvectorSchema.create(1)),
            Optional.of(SszBitvectorSchema.create(1).of(true)),
            "0101",
            "56d8a66fbae0300efba7ec2c531973aaae22e7a2ed6ded081b5b32d07a32780a"),
        Arguments.of(
            SszOptionalSchema.create(SszBitvectorSchema.create(9)),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(SszBitvectorSchema.create(9)),
            Optional.of(
                SszBitvectorSchema.create(9)
                    .of(true, true, true, true, false, true, true, true, true)),
            "01ef01",
            "68c6569b2cedecc2c3c609b444e7a884145395659a4e6a6ead5c666167b91f0c"),
        Arguments.of(
            SszOptionalSchema.create(SszBitlistSchema.create(1)),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(SszBitlistSchema.create(1)),
            Optional.of(SszBitlistSchema.create(1).of(true)),
            "0103",
            "ed84d8c963f19fe2e3c8b06732831b3b67f3c0d9ae3a455e567916237b11f78c"),
        Arguments.of(
            SszOptionalSchema.create(SszBitlistSchema.create(9)),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(SszBitlistSchema.create(9)),
            Optional.of(SszBitlistSchema.create(9).of(true)),
            "0103",
            "ed84d8c963f19fe2e3c8b06732831b3b67f3c0d9ae3a455e567916237b11f78c"),
        Arguments.of(
            SszOptionalSchema.create(
                SszUnionSchema.create(
                    SszPrimitiveSchemas.NONE_SCHEMA,
                    SszPrimitiveSchemas.UINT64_SCHEMA,
                    SszPrimitiveSchemas.UINT256_SCHEMA)),
            Optional.empty(),
            "",
            "f5a5fd42d16a20302798ef6ed309979b43003d2320d9f0e8ea9831a92759fb4b"),
        Arguments.of(
            SszOptionalSchema.create(
                SszUnionSchema.create(
                    SszPrimitiveSchemas.NONE_SCHEMA,
                    SszPrimitiveSchemas.UINT64_SCHEMA,
                    SszPrimitiveSchemas.UINT256_SCHEMA)),
            Optional.of(
                SszUnionSchema.create(
                        SszPrimitiveSchemas.NONE_SCHEMA,
                        SszPrimitiveSchemas.UINT64_SCHEMA,
                        SszPrimitiveSchemas.UINT256_SCHEMA)
                    .createFromValue(0, SszNone.INSTANCE)),
            "0100",
            "e832d263aaa8f9417d9f45a702834f6961ee7b15ad4d3d27f2b0f4fe79d33031"),
        Arguments.of(
            SszOptionalSchema.create(
                SszUnionSchema.create(
                    SszPrimitiveSchemas.NONE_SCHEMA,
                    SszPrimitiveSchemas.UINT64_SCHEMA,
                    SszPrimitiveSchemas.UINT256_SCHEMA)),
            Optional.of(
                SszUnionSchema.create(
                        SszPrimitiveSchemas.NONE_SCHEMA,
                        SszPrimitiveSchemas.UINT64_SCHEMA,
                        SszPrimitiveSchemas.UINT256_SCHEMA)
                    .createFromValue(1, SszUInt64.of(UInt64.valueOf(64)))),
            "01014000000000000000",
            "ff3f9699283db0b019849434dd0fc77a02e8a4bc5b88f8984fa5cee66effcd0d"),
        Arguments.of(
            SszOptionalSchema.create(
                SszUnionSchema.create(
                    SszPrimitiveSchemas.NONE_SCHEMA,
                    SszPrimitiveSchemas.UINT64_SCHEMA,
                    SszPrimitiveSchemas.UINT256_SCHEMA)),
            Optional.of(
                SszUnionSchema.create(
                        SszPrimitiveSchemas.NONE_SCHEMA,
                        SszPrimitiveSchemas.UINT64_SCHEMA,
                        SszPrimitiveSchemas.UINT256_SCHEMA)
                    .createFromValue(2, SszUInt256.of(UInt256.valueOf(32)))),
            "01022000000000000000000000000000000000000000000000000000000000000000",
            "56916900921779bd56b5211516c6a4ede7158df77d0ec4b1376aeca05db9606b"));
  }

  private static class Foo extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<Foo> SSZ_SCHEMA =
        SszContainerSchema.create(
            "Foo",
            List.of(
                AbstractSszContainerSchema.NamedSchema.of(
                    "value1", SszPrimitiveSchemas.UINT64_SCHEMA),
                AbstractSszContainerSchema.NamedSchema.of(
                    "value2", SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)),
                AbstractSszContainerSchema.NamedSchema.of(
                    "value3", SszOptionalSchema.create(SszPrimitiveSchemas.UINT256_SCHEMA))),
            Foo::new);

    private Foo(final SszContainerSchema<Foo> type, final TreeNode backingNode) {
      super(type, backingNode);
    }
  }

  private static class ContainerWithOptionals extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<ContainerWithOptionals> SSZ_SCHEMA =
        SszContainerSchema.create(
            "TestSmallContainer",
            List.of(
                AbstractSszContainerSchema.NamedSchema.of(
                    "a", SszOptionalSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA)),
                AbstractSszContainerSchema.NamedSchema.of("b", SszPrimitiveSchemas.BYTES32_SCHEMA),
                AbstractSszContainerSchema.NamedSchema.of(
                    "c", SszOptionalSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA)),
                AbstractSszContainerSchema.NamedSchema.of("d", SszPrimitiveSchemas.BYTES32_SCHEMA),
                AbstractSszContainerSchema.NamedSchema.of(
                    "e", SszOptionalSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA))),
            ContainerWithOptionals::new);

    private ContainerWithOptionals(
        final SszContainerSchema<ContainerWithOptionals> type, final TreeNode backingNode) {
      super(type, backingNode);
    }
  }
}
