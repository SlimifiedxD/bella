package me.slimified.bella.processor;

import com.palantir.javapoet.*;
import me.slimified.bella.core.repository.Repository;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.sql.DataSource;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.sql.*;
import java.util.*;

@SupportedAnnotationTypes("me.slimified.bella.core.annotation.Persist")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class PersistProcessor extends AbstractProcessor {
    private static final String BASE_GENERATED_PACKAGE = "me.slimified.bella.generated";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (final TypeElement annotation : annotations) {
            final Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            final Set<GeneratedRepositoryType> generatedRepositories = new HashSet<>();
            for (final Element element : annotatedElements) {
                String table = "";
                for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
                    if (!mirror.getAnnotationType().asElement().equals(annotation)) continue;

                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {
                        table = (String) entry.getValue().getValue();
                    }
                }

                final ElementKind kind = element.getKind();
                if (!kind.isClass()) continue;
                final TypeElement typeElement = (TypeElement) element;
                final TypeName typeName = TypeName.get(typeElement.asType());
                final List<? extends Element> elements;
                if (kind == ElementKind.CLASS) {
                    elements = typeElement.getEnclosedElements()
                            .stream()
                            .filter(e -> e.getKind().isField())
                            .toList();
                } else if (kind == ElementKind.RECORD) {
                    elements = typeElement.getRecordComponents();
                } else {
                    continue;
                }
                final int elementsSize = elements.size();
                final StringBuilder createSchema = new StringBuilder("(");
                createSchema.append("id INT PRIMARY KEY AUTOINCREMENT, ");
                for (int i = 0; i < elementsSize; i++) {
                    final Element e = elements.get(i);
                    createSchema
                            .append(e.getSimpleName())
                            .append(" ")
                            .append(typeToSqlTypeName(e));
                    if (i != elementsSize - 1) {
                        createSchema.append(", ");
                    } else {
                        createSchema.append(")");
                    }
                }
                final StringBuilder schema = new StringBuilder("(");
                for (int i = 0; i < elementsSize; i++) {
                    final Element e = elements.get(i);
                    schema.append(e.getSimpleName());
                    if (i != elementsSize - 1) {
                        schema.append(", ");
                    } else {
                        schema.append(")");
                    }
                }
                final StringBuilder fields = new StringBuilder();
                for (int i = 0; i < elementsSize; i++) {
                    final Element e =  elements.get(i);
                    if (e instanceof RecordComponentElement) {
                        fields.append("value.").append(e.getSimpleName()).append("()");
                    } else {
                        fields.append("value.").append(e.getSimpleName());
                    }
                    if (i < elementsSize - 1) {
                        fields.append(", ");
                    }
                }

                final ParameterSpec typeParameter = ParameterSpec
                        .builder(typeName, "value")
                        .build();
                final MethodSpec insertMethod = MethodSpec
                        .methodBuilder("insert")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(typeParameter)
                        .returns(long.class)
                        .beginControlFlow("try (final $T conn = dataSource.getConnection())", Connection.class)
                        .addStatement("createTableIfNotExists()")
                        .addStatement("final $T builder = new StringBuilder($S)", StringBuilder.class, "(")
                        .addStatement("final Object[] fields = new Object[] { $L }", fields.toString())
                        .beginControlFlow("for (int i = 0; i < fields.length; i++)")
                        .addStatement("builder.append($S)", "?")
                        .beginControlFlow("if (i < fields.length - 1)")
                        .addStatement("builder.append($S)", ", ")
                        .endControlFlow()
                        .endControlFlow()
                        .addStatement("builder.append($S)", ")")
                        .addStatement("final $T ps = conn.prepareStatement($S + table + $S + $S + builder.toString(), $T.RETURN_GENERATED_KEYS)",
                                PreparedStatement.class,
                                "INSERT INTO ",
                                " " + schema,
                                " VALUES ",
                                Statement.class
                                )
                        .beginControlFlow("for (int i = 0; i < fields.length; i++)")
                        .addStatement("final Object field = fields[i]")
                        .beginControlFlow("if (field instanceof Integer integer)")
                        .addStatement("ps.setInt(i + 1, integer)")
                        .nextControlFlow("else if (field instanceof String string)")
                        .addStatement("ps.setString(i + 1, string)")
                        .endControlFlow()
                        .endControlFlow()
                        .addStatement("ps.executeUpdate()")
                        .beginControlFlow("try ($T rs = ps.getGeneratedKeys())", ResultSet.class)
                        .beginControlFlow("if (rs.next())")
                        .addStatement("return rs.getLong(1)")
                        .endControlFlow()
                        .endControlFlow()
                        .nextControlFlow("catch ($T e)", SQLException.class)
                        .addStatement("e.printStackTrace()")
                        .endControlFlow()
                        .addStatement("throw new $T($S)", RuntimeException.class, "Something went wrong whilst inserting!")
                        .build();
                final MethodSpec findByIdMethod = MethodSpec
                        .methodBuilder("findById")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(TypeName.LONG, "id")
                        .returns(ParameterizedTypeName.get(ClassName.get(Optional.class), typeName))
                        .addStatement("return Optional.empty()")
                        .build();
                final FieldSpec dataSourceField = FieldSpec
                        .builder(DataSource.class, "dataSource", Modifier.PRIVATE, Modifier.FINAL)
                        .build();
                final FieldSpec tableField = FieldSpec
                        .builder(String.class, "table", Modifier.PRIVATE, Modifier.FINAL)
                        .build();
                final MethodSpec createTableSpec = MethodSpec
                        .methodBuilder("createTableIfNotExists")
                        .addModifiers(Modifier.PRIVATE)
                        .beginControlFlow("try (final $T conn = dataSource.getConnection())", Connection.class)
                        .addStatement("final $T stmt = conn.createStatement()", Statement.class)
                        .addStatement("stmt.execute($S)", "CREATE TABLE IF NOT EXISTS " + table + createSchema) // TODO: fix this shit by putting proper schema definition
                        .nextControlFlow("catch ($T e)", SQLException.class)
                        .addStatement("e.printStackTrace()")
                        .endControlFlow()
                        .build();
                final MethodSpec constructorSpec = MethodSpec
                        .constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(DataSource.class, "dataSource")
                        .addParameter(String.class, "table")
                        .addStatement("this.dataSource = dataSource")
                        .addStatement("this.table = table")
                        .build();
                final TypeName newTypeName = ClassName.get(BASE_GENERATED_PACKAGE, typeElement.getSimpleName() + "RepositoryImpl");
                final TypeSpec repositoryImplType = TypeSpec
                        .classBuilder(typeElement.getSimpleName().toString() + "RepositoryImpl")
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                        .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Repository.class), typeName))
                        .addMethod(insertMethod)
                        .addMethod(constructorSpec)
                        .addMethod(createTableSpec)
                        .addField(dataSourceField)
                        .addField(tableField)
                        .addMethod(findByIdMethod)
                        .build();
                final JavaFile generatedRepository = JavaFile
                        .builder(BASE_GENERATED_PACKAGE, repositoryImplType)
                        .build();

                try {
                    generatedRepositories.add(new GeneratedRepositoryType(typeName, newTypeName, table));
                    generatedRepository.writeTo(processingEnv.getFiler());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            final MethodSpec.Builder getRepositorySpecBuilder = MethodSpec
                    .methodBuilder("getRepository")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(DataSource.class, "dataSource")
                    .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class)), "clazz")
                    .beginControlFlow("if (false)")
                    .addStatement("return null")
                    .returns(Repository.class);
            generatedRepositories.forEach(repo -> {
                getRepositorySpecBuilder
                        .nextControlFlow("else if (clazz == $T.class)", repo.valueType())
                        .addStatement("return new $T(dataSource, $S)", repo.repositoryType(), repo.table());
            });
            getRepositorySpecBuilder
                    .nextControlFlow("else")
                    .addStatement("throw new $T($S)", IllegalArgumentException.class, "Class is not annotated with @Persist!")
                    .endControlFlow();
            final TypeSpec repositoryManagerType = TypeSpec
                    .classBuilder("RepositoryManager")
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .addMethod(getRepositorySpecBuilder.build())
                    .build();
            final JavaFile generatedRepositoryManager = JavaFile
                    .builder(BASE_GENERATED_PACKAGE, repositoryManagerType)
                    .build();

            try {
                generatedRepositoryManager.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }
    private String typeToSqlTypeName(Element element) {
        final TypeMirror mirror = element.asType();
        return switch (mirror.getKind()) {
            case INT -> "INT";
            case LONG -> "BIGINT";
            case FLOAT -> "REAL";
            case DOUBLE -> "DOUBLE";
            case BOOLEAN -> "BOOLEAN";
            case CHAR, BYTE, SHORT -> "SMALLINT";
            case DECLARED -> declaredTypeToSql(mirror);

            default -> "TEXT";
        };
    }

    private String declaredTypeToSql(TypeMirror mirror) {
        String name = mirror.toString();

        return switch (name) {
            case "java.lang.String" -> "VARCHAR";
            default -> "TEXT";
        };
    }
}
