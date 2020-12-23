package co.uk.jacobmountain;

import co.uk.jacobmountain.utils.StringUtils;
import co.uk.jacobmountain.visitor.MethodDetails;
import co.uk.jacobmountain.visitor.MethodDetailsVisitor;
import com.squareup.javapoet.*;
import graphql.schema.idl.TypeDefinitionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static graphql.language.TypeName.newTypeName;

@Slf4j
@RequiredArgsConstructor
public class ClientGenerator {

    private final Filer filer;

    private final int maxDepth;

    private final TypeMapper typeMapper;

    private final String packageName;

    private final String dtoPackageName;

    public ClientGenerator(Filer filer, int maxDepth, TypeMapper typeMapper, String packageName) {
        this.filer = filer;
        this.maxDepth = maxDepth;
        this.typeMapper = typeMapper;
        this.packageName = packageName;
        this.dtoPackageName = packageName + ".dto";
    }

    private ParameterizedTypeName generateTypeName(TypeDefinitionRegistry schema) {
        ClassName fetcher = ClassName.get(Fetcher.class);
        ClassName query = ClassName.get(this.dtoPackageName, "Query");
        if (schema.hasType(newTypeName("Mutation").build()))
            return ParameterizedTypeName.get(
                    fetcher,
                    query,
                    ClassName.get(this.dtoPackageName, "Mutation")
            );
        return ParameterizedTypeName.get(
                fetcher,
                query,
                ClassName.get(Void.class)
        );
    }

    @SneakyThrows
    public void generate(TypeDefinitionRegistry schema, TypeElement element) {
        ParameterizedTypeName fetcherType = generateTypeName(schema);
        TypeSpec.Builder builder = TypeSpec.classBuilder(element.getSimpleName() + "Graph")
                .addSuperinterface(ClassName.get(element))
                .addModifiers(Modifier.PUBLIC)
                .addField(fetcherType, "fetcher", Modifier.PRIVATE, Modifier.FINAL);

        generateConstructor(builder, fetcherType);

        element.getEnclosedElements()
                .stream()
                .map(method -> generateImpl(method, schema))
                .forEach(builder::addMethod);

        writeToFile(builder.build());
    }

    private void generateConstructor(TypeSpec.Builder builder, ParameterizedTypeName fetcherType) {
        builder.addMethod(
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(fetcherType, "fetcher")
                        .addStatement("this.fetcher = fetcher")
                        .build()
        );
    }

    private MethodSpec generateImpl(Element method, TypeDefinitionRegistry schema) {
        MethodDetails details = method.accept(new MethodDetailsVisitor(), typeMapper);
        log.info("{}", details.getReturnType());
        MethodSpec.Builder builder = MethodSpec.methodBuilder(method.getSimpleName().toString())
                .returns(details.getReturnType())
                .addModifiers(Modifier.PUBLIC)
                .addParameters(details.getParameters());
        assembleArguments(details).forEach(builder::addStatement);
        assembleFetchAndReturn(details, schema).forEach(builder::addStatement);
        return builder.build();
    }

    private List<CodeBlock> assembleFetchAndReturn(MethodDetails details, TypeDefinitionRegistry schema) {
        boolean wrapInOptional = details.getReturnType() instanceof ParameterizedTypeName &&
                ((ParameterizedTypeName) details.getReturnType()).rawType.equals(ClassName.get(Optional.class));
        CodeBlock.Builder builder = CodeBlock.builder();
        if (wrapInOptional) {
            builder.add("return $T.ofNullable(\n", Optional.class)
                    .indent();
        } else {
            builder.add("return ");
        }
        builder.add("fetcher")
                .add(generateQuery(schema, details))
                .add("\n").indent()
                .add(".getData()")
                .add("\n")
                .add(".$L()", StringUtils.camelCase("get", details.getField()));
        if (wrapInOptional) {
            builder.add("\n)").unindent();
        }
        builder.unindent();
        return Collections.singletonList(builder.build());
    }

    private CodeBlock generateQuery(TypeDefinitionRegistry schema, MethodDetails details) {
        String query = new QueryGenerator(schema, maxDepth).generateQuery(details.getField(), details.isMutation());
        boolean hasArgs = details.hasParameters();
        return CodeBlock.of(
                String.format(".%s(\"$L\", %s)", details.isQuery() ? "query" : "mutate", hasArgs ? "args" : "null"),
                query
        );
    }

    public static String generateArgumentClassname(String field) {
        return StringUtils.capitalize(field) + "Arguments";
    }

    private List<CodeBlock> assembleArguments(MethodDetails details) {
        List<ParameterSpec> parameters = details.getParameters();
        if (parameters.isEmpty()) {
            return Collections.emptyList();
        }
        List<CodeBlock> ret = new ArrayList<>();
        TypeName type = ClassName.get(dtoPackageName, generateArgumentClassname(details.getField()));
        ret.add(CodeBlock.of("$T args = new $T()", type, type));
        details.getParameters()
                .forEach(param -> ret.add(CodeBlock.of("args.set$L($L)", StringUtils.capitalize(param.name), param.name)));
        return ret;
    }

    private void writeToFile(TypeSpec spec) throws Exception {
        JavaFile.builder(packageName, spec)
                .indent("\t")
                .skipJavaLangImports(true)
                .build()
                .writeTo(filer);
    }

}

