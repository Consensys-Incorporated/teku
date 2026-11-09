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

package tech.pegasys.teku.infrastructure.json.types;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import java.util.Objects;
import java.util.function.Predicate;

public class OneOfTypeTestTypeDefinition {
  public interface TestType {
    String getName();

    void setName(final String name);
  }

  public static final DeserializableTypeDefinition<TestObjA> TYPE_A =
      DeserializableTypeDefinition.object(TestObjA.class)
          .name("TA")
          .initializer(TestObjA::new)
          .withField("value1", STRING_TYPE, TestType::getName, TestType::setName)
          .build();
  public static final DeserializableTypeDefinition<TestObjB> TYPE_B =
      DeserializableTypeDefinition.object(TestObjB.class)
          .name("TB")
          .initializer(TestObjB::new)
          .withField("value2", STRING_TYPE, TestType::getName, TestType::setName)
          .build();

  public static final SerializableOneOfTypeDefinition<TestType>
      SERIALIZABLE_ONE_OF_TYPE_DEFINITION =
          new SerializableOneOfTypeDefinitionBuilder<TestType>()
              .description("meaningful description")
              .withType(TestObjA.IS_INSTANCE, TYPE_A)
              .withType(TestObjB.IS_INSTANCE, TYPE_B)
              .build();

  public static class TestObjA implements TestType {
    private String name;

    public TestObjA() {}

    public TestObjA(final String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public void setName(final String name) {
      this.name = name;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final TestObjA testObjA = (TestObjA) o;
      return Objects.equals(name, testObjA.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }

    static final Predicate<TestType> IS_INSTANCE = testType -> testType instanceof TestObjA;
  }

  public static class TestObjB implements TestType {
    private String name;

    public TestObjB() {}

    public TestObjB(final String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public void setName(final String name) {
      this.name = name;
    }

    static final Predicate<TestType> IS_INSTANCE = testType -> testType instanceof TestObjB;
  }
}
