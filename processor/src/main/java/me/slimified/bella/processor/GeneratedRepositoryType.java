package me.slimified.bella.processor;

import com.palantir.javapoet.TypeName;

public record GeneratedRepositoryType(TypeName valueType, TypeName repositoryType, String table) {
}
