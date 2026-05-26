package io.gauss.vela.generator;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers HU-006 AC-5 (interface generation), AC-6 (enum generation).
 */
class InterfaceGeneratorTest {

    private final InterfaceGenerator gen = new InterfaceGenerator();

    // -----------------------------------------------------------------------
    // AC-5: TypeScript interface from POJO
    // -----------------------------------------------------------------------

    @Test
    void simpleDto_generatesInterface() {
        String ts = gen.generate(UserDto.class);
        assertThat(ts).startsWith("export interface UserDto {");
        assertThat(ts).contains("  id: number;");
        assertThat(ts).contains("  name: string;");
        assertThat(ts).contains("  email: string;");
        assertThat(ts).endsWith("}");
    }

    @Test
    void listField_generatesArrayType() {
        String ts = gen.generate(ProductDto.class);
        assertThat(ts).contains("  tags: string[];");
    }

    @Test
    void optionalField_generatesUndefinable() {
        String ts = gen.generate(ProductDto.class);
        assertThat(ts).contains("  description: string | undefined;");
    }

    @Test
    void nullableAnnotatedField_generatesNullable() {
        String ts = gen.generate(ProductDto.class);
        assertThat(ts).contains("  discount: number | null;");
    }

    @Test
    void staticField_isSkipped() {
        String ts = gen.generate(UserDto.class);
        assertThat(ts).doesNotContain("CONSTANT");
    }

    @Test
    void nestedDtoField_isTrackedAsReference() {
        gen.generate(OrderDto.class);
        assertThat(gen.referencedTypes()).contains(UserDto.class);
    }

    // -----------------------------------------------------------------------
    // AC-6: TypeScript enum from Java enum
    // -----------------------------------------------------------------------

    @Test
    void enum_generatesEnumBlock() {
        String ts = gen.generate(Status.class);
        assertThat(ts).startsWith("export enum Status {");
        assertThat(ts).contains("  ACTIVE,");
        assertThat(ts).contains("  INACTIVE,");
        assertThat(ts).contains("  PENDING");
        assertThat(ts).endsWith("}");
    }

    @Test
    void enum_lastConstant_hasNoTrailingComma() {
        String ts = gen.generate(Status.class);
        assertThat(ts).doesNotContain("PENDING,");
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    @SuppressWarnings("unused")
    static class UserDto {
        static final String CONSTANT = "v1";
        long id;
        String name;
        String email;
    }

    @SuppressWarnings("unused")
    static class ProductDto {
        String name;
        List<String> tags;
        Optional<String> description;
        @jakarta.annotation.Nullable Double discount;
    }

    @SuppressWarnings("unused")
    static class OrderDto {
        long orderId;
        UserDto customer;
    }

    enum Status { ACTIVE, INACTIVE, PENDING }
}
