package com.prelude.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.domain.JavaClass;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Every row type a mapper reads must name its table explicitly.
 *
 * <p>MyBatis-Plus falls back to a camel-case-to-underscore guess of the class name when
 * {@code @TableName} is absent, which is configured through {@code map-underscore-to-camel-case}
 * in {@code application.yml}. That guess silently rewrites the table when the domain class is
 * renamed, so a row type whose name drifts from its table fails only at runtime with a 500.
 * {@code PositionTemplate} was renamed to {@code Position} and its mapping broke exactly this
 * way. Naming the table on the row type, and proving here that the name is one the schema
 * actually creates, turns that rename into a compile-time-red test instead.
 */
class MapperTableNameTest {

    private static final Pattern CREATED_TABLE =
        Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`\\[]?([A-Za-z0-9_]+)[`\\]]?", Pattern.CASE_INSENSITIVE);

    private static Set<String> createdTables;
    private static List<String> unverifiedRows;

    @BeforeAll
    static void readTheSchema() throws Exception {
        createdTables = tablesCreatedByMigrations();
        unverifiedRows = new ArrayList<>();
    }

    @Test
    @DisplayName("Every row type a mapper binds names a table the schema creates")
    void everyMapperRowTypeNamesACreatedTable() {
        var rowTypes = new TreeSet<String>();
        for (JavaClass candidate : new ClassFileImporter().importPackages("com.prelude..")) {
            if (!candidate.isAssignableTo(BaseMapper.class)) {
                continue;
            }
            Class<?> rowType = rowTypeOf(candidate);
            if (rowType == null) {
                continue;
            }
            rowTypes.add(rowType.getName());
            TableName binding = rowType.getAnnotation(TableName.class);
            assertThat(binding)
                .as("%s is bound by a BaseMapper, so it must declare @TableName rather than rely on"
                    + " the camel-case guess in application.yml", rowType.getName())
                .isNotNull();
            assertThat(binding.value())
                .as("@TableName on %s", rowType.getName())
                .isNotBlank();
            assertThat(createdTables)
                .as("@TableName(\"%s\") on %s must name a table the Flyway migrations create", binding.value(), rowType.getName())
                .contains(binding.value());
        }

        // Guards against the mapper discovery itself silently finding nothing.
        assertThat(rowTypes).hasSizeGreaterThanOrEqualTo(19);
    }

    /**
     * The schema itself must keep creating every table the mappers bind. Without this, renaming
     * a table in the migration would leave the row type pointing at a table that no longer exists.
     */
    @Test
    @DisplayName("Every table a mapper binds is still created by the schema")
    void everyBoundTableStillExistsInTheSchema() throws Exception {
        var boundTables = new TreeSet<String>();
        for (JavaClass candidate : new ClassFileImporter().importPackages("com.prelude..")) {
            if (!candidate.isAssignableTo(BaseMapper.class)) {
                continue;
            }
            Class<?> rowType = rowTypeOf(candidate);
            if (rowType == null) {
                continue;
            }
            TableName binding = rowType.getAnnotation(TableName.class);
            if (binding != null && !binding.value().isBlank()) {
                boundTables.add(binding.value());
            }
        }

        assertThat(boundTables).isNotEmpty();
        assertThat(createdTables).containsAll(boundTables);
    }

    private static Class<?> rowTypeOf(JavaClass mapper) {
        Class<?> resolved;
        try {
            resolved = Class.forName(mapper.getName());
        } catch (ClassNotFoundException failure) {
            unverifiedRows.add(mapper.getName());
            return null;
        }
        for (Class<?> current = resolved; current != null; current = current.getSuperclass()) {
            for (Type bound : current.getGenericInterfaces()) {
                Class<?> rowType = baseMapperArgumentOf(bound);
                if (rowType != null) {
                    return rowType;
                }
            }
        }
        return null;
    }

    private static Class<?> baseMapperArgumentOf(Type bound) {
        if (!(bound instanceof ParameterizedType parameterized)) {
            return null;
        }
        if (!BaseMapper.class.equals(parameterized.getRawType())) {
            return null;
        }
        Type argument = parameterized.getActualTypeArguments()[0];
        return argument instanceof Class<?> type ? type : null;
    }

    private static Set<String> tablesCreatedByMigrations() throws IOException {
        var tables = new HashSet<String>();
        var resolver = new PathMatchingResourcePatternResolver();
        for (Resource migration : resolver.getResources("classpath*:db/migration/*.sql")) {
            String script;
            try (InputStream stream = migration.getInputStream()) {
                script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            Matcher matcher = CREATED_TABLE.matcher(script);
            while (matcher.find()) {
                tables.add(matcher.group(1));
            }
        }
        return tables;
    }
}
