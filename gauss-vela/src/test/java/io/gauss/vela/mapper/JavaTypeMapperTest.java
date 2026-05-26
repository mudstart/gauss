package io.gauss.vela.mapper;

import io.gauss.vela.model.TsType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers HU-006 AC-1: primitives, AC-2: collections/maps, AC-3: Optional, AC-4: @Nullable.
 */
class JavaTypeMapperTest {

    private final JavaTypeMapper mapper = JavaTypeMapper.INSTANCE;

    // -----------------------------------------------------------------------
    // AC-1: Primitive and common Java types
    // -----------------------------------------------------------------------

    @Test
    void string_mapsTo_string() {
        assertThat(mapper.map(String.class).render()).isEqualTo("string");
    }

    @Test
    void intPrimitive_mapsTo_number() {
        assertThat(mapper.map(int.class).render()).isEqualTo("number");
    }

    @Test
    void longBoxed_mapsTo_number() {
        assertThat(mapper.map(Long.class).render()).isEqualTo("number");
    }

    @Test
    void doublePrimitive_mapsTo_number() {
        assertThat(mapper.map(double.class).render()).isEqualTo("number");
    }

    @Test
    void bool_mapsTo_boolean() {
        assertThat(mapper.map(boolean.class).render()).isEqualTo("boolean");
        assertThat(mapper.map(Boolean.class).render()).isEqualTo("boolean");
    }

    @Test
    void voidType_mapsTo_void() {
        assertThat(mapper.map(void.class).render()).isEqualTo("void");
    }

    @Test
    void objectType_mapsTo_unknown() {
        assertThat(mapper.map(Object.class).render()).isEqualTo("unknown");
    }

    // -----------------------------------------------------------------------
    // AC-2: Generics — List, Map
    // -----------------------------------------------------------------------

    @Test
    void listOfString_mapsTo_stringArray() throws Exception {
        Field f = Fixture.class.getDeclaredField("tags");
        assertThat(mapper.map(f.getGenericType()).render()).isEqualTo("string[]");
    }

    @Test
    void setOfInteger_mapsTo_numberArray() throws Exception {
        Field f = Fixture.class.getDeclaredField("scores");
        assertThat(mapper.map(f.getGenericType()).render()).isEqualTo("number[]");
    }

    @Test
    void mapStringToObject_mapsTo_recordUnknown() throws Exception {
        Field f = Fixture.class.getDeclaredField("metadata");
        assertThat(mapper.map(f.getGenericType()).render()).isEqualTo("Record<string, unknown>");
    }

    // -----------------------------------------------------------------------
    // AC-3: Optional<T> → T | undefined
    // -----------------------------------------------------------------------

    @Test
    void optionalString_mapsTo_stringOrUndefined() throws Exception {
        Field f = Fixture.class.getDeclaredField("nickname");
        assertThat(mapper.map(f.getGenericType()).render()).isEqualTo("string | undefined");
    }

    @Test
    void optionalLong_mapsTo_numberOrUndefined() throws Exception {
        Field f = Fixture.class.getDeclaredField("externalId");
        assertThat(mapper.map(f.getGenericType()).render()).isEqualTo("number | undefined");
    }

    // -----------------------------------------------------------------------
    // AC-4: @Nullable annotation
    // -----------------------------------------------------------------------

    @Test
    void nullableAnnotation_addsOrNull() throws Exception {
        Field f = Fixture.class.getDeclaredField("description");
        TsType result = mapper.map(f.getGenericType(), f);
        assertThat(result.render()).isEqualTo("string | null");
        assertThat(result.isNullable()).isTrue();
    }

    @Test
    void noAnnotation_noNullSuffix() throws Exception {
        Field f = Fixture.class.getDeclaredField("name");
        TsType result = mapper.map(f.getGenericType(), f);
        assertThat(result.render()).isEqualTo("string");
        assertThat(result.isNullable()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Fixture DTO used via reflection
    // -----------------------------------------------------------------------

    @SuppressWarnings("unused")
    static class Fixture {
        String name;
        List<String> tags;
        Set<Integer> scores;
        Map<String, Object> metadata;
        Optional<String> nickname;
        Optional<Long> externalId;
        @jakarta.annotation.Nullable String description;
    }
}
