package me.slimified.bella.processor;

import com.palantir.javapoet.*;
import me.slimified.bella.core.repository.Repository;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

                if (!element.getKind().isClass()) continue;
                final TypeElement typeElement = (TypeElement) element;
                final TypeName typeName = TypeName.get(typeElement.asType());

                final ParameterSpec typeParameter = ParameterSpec
                        .builder(typeName, "value")
                        .build();
                final MethodSpec insertMethod = MethodSpec
                        .methodBuilder("insert")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(typeParameter)
                        .returns(long.class)
                        .beginControlFlow("try")
                        .addStatement("final $T conn = dataSource.getConnection()", Connection.class)
                        .addStatement("final $T builder = new StringBuilder()", StringBuilder.class)
                        .addStatement("final Object[] fields = new Object[] {}")
                        .beginControlFlow("for (int i = 0; i < fields.length; i++)")
                        .beginControlFlow("if (i == fields.length - 1)")
                        .addStatement("builder.append($S)", "?)")
                        .nextControlFlow("else")
                        .addStatement("builder.append($S)", "?, ")
                        .endControlFlow()
                        .endControlFlow()
                        .addStatement("final $T ps = conn.prepareStatement($S + table + $S + builder.toString())",
                                PreparedStatement.class,
                                "INSERT INTO ",
                                " VALUES ")
                        .beginControlFlow("for (int i = 0; i < fields.length; i++)")
                        .addStatement("final Object field = fields[i]")
                        .beginControlFlow("if (field instanceof Integer integer)")
                        .addStatement("ps.setInt(i + 1, integer)")
                        .nextControlFlow("else if (field instanceof String string)")
                        .addStatement("ps.setString(i + 1, string)")
                        .endControlFlow()
                        .addStatement("ps.executeUpdate()")
                        .beginControlFlow("try ($T rs = ps.getGeneratedKeys())", ResultSet.class)
                        .beginControlFlow("if (rs.next())")
                        .addStatement("return rs.getLong(1)")
                        .endControlFlow()
                        .endControlFlow()
                        .endControlFlow()
                        .nextControlFlow("catch ($T e)", SQLException.class)
                        .addStatement("e.printStackTrace()")
                        .endControlFlow()
                        .addStatement("throw new $T($S)", RuntimeException.class, "Shit has hit the fan. It is all over the walls and ceiling.")
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
                        .beginControlFlow("try")
                        .addStatement("final $T conn = dataSource.getConnection()", Connection.class)
                        .addStatement("final $T stmt = conn.createStatement()", Statement.class)
                        .addStatement("stmt.execute($S)", "CREATE TABLE IF NOT EXISTS " + table + " ()") // TODO: fix this shit by putting proper schema definition
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
}
