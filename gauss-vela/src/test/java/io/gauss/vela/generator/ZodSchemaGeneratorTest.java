package io.gauss.vela.generator;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers HU-009: Jakarta validation annotations mirrored as Zod constraints.
 */
class ZodSchemaGeneratorTest {

    private final ZodSchemaGenerator gen = ZodSchemaGenerator.INSTANCE;

    // -----------------------------------------------------------------------
    // AC-1: @NotNull → z.string().min(1)
    // -----------------------------------------------------------------------

    @Test
    void notNull_onString_generatesMin1() {
        String ts = gen.generate(UserForm.class);
        assertThat(ts).contains("name: z.string().min(1),");
    }

    // -----------------------------------------------------------------------
    // AC-2: @Min / @Max → z.number().min(n).max(n)
    // -----------------------------------------------------------------------

    @Test
    void min_generatesNumberMin() {
        String ts = gen.generate(UserForm.class);
        assertThat(ts).contains("age: z.number().min(18).max(120),");
    }

    // -----------------------------------------------------------------------
    // AC-3: @Email → z.string().email()
    // -----------------------------------------------------------------------

    @Test
    void email_generatesStringEmail() {
        String ts = gen.generate(UserForm.class);
        assertThat(ts).contains("email: z.string().email(),");
    }

    // -----------------------------------------------------------------------
    // AC-4: @Size(min, max) → z.string().min(min).max(max)
    // -----------------------------------------------------------------------

    @Test
    void size_generatesMinMax() {
        String ts = gen.generate(UserForm.class);
        assertThat(ts).contains("bio: z.string().min(10).max(500),");
    }

    // -----------------------------------------------------------------------
    // @Nullable → .nullable()
    // -----------------------------------------------------------------------

    @Test
    void nullable_appendsNullable() {
        String ts = gen.generate(UserForm.class);
        assertThat(ts).contains("nickname: z.string().nullable(),");
    }

    // -----------------------------------------------------------------------
    // AC-5: schema exported alongside TS type
    // -----------------------------------------------------------------------

    @Test
    void output_exportsSchemaConstant() {
        String ts = gen.generate(UserForm.class);
        assertThat(ts).contains("export const UserFormSchema = z.object({");
    }

    @Test
    void output_exportsInferredType() {
        String ts = gen.generate(UserForm.class);
        assertThat(ts).contains("export type UserForm = z.infer<typeof UserFormSchema>;");
    }

    @Test
    void output_importsZod() {
        String ts = gen.generate(UserForm.class);
        assertThat(ts).contains("import { z } from 'zod';");
    }

    // -----------------------------------------------------------------------
    // AC-6: React Hook Form integration comment in output
    // -----------------------------------------------------------------------

    @Test
    void output_containsReactHookFormSnippet() {
        String ts = gen.generate(UserForm.class);
        assertThat(ts).contains("zodResolver(UserFormSchema)");
        assertThat(ts).contains("useForm<UserForm>");
    }

    // -----------------------------------------------------------------------
    // Optional<T> → .optional()
    // -----------------------------------------------------------------------

    @Test
    void optional_generatesOptionalChain() {
        String ts = gen.generate(UserForm.class);
        assertThat(ts).contains("website: z.string().optional(),");
    }

    // -----------------------------------------------------------------------
    // List<T> → z.array(...)
    // -----------------------------------------------------------------------

    @Test
    void list_generatesArray() {
        String ts = gen.generate(UserForm.class);
        assertThat(ts).contains("tags: z.array(z.string()),");
    }

    // -----------------------------------------------------------------------
    // File name convention
    // -----------------------------------------------------------------------

    @Test
    void fileName_isClassNameSchemaDotTs() {
        assertThat(gen.fileName(UserForm.class)).isEqualTo("UserFormSchema.ts");
    }

    // -----------------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------------

    @SuppressWarnings("unused")
    static class UserForm {
        @NotNull  String name;
        @Email    String email;
        @Min(18) @Max(120) int age;
        @Size(min = 10, max = 500) String bio;
        @Nullable String nickname;
        Optional<String> website;
        List<String> tags;
    }
}
