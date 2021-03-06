package com.jacobmountain.graphql.client;

import com.jacobmountain.graphql.client.modules.*;
import com.jacobmountain.graphql.client.query.QueryGenerator;
import com.jacobmountain.graphql.client.utils.AnnotationUtils;
import com.jacobmountain.graphql.client.utils.Schema;
import com.jacobmountain.graphql.client.utils.StringUtils;
import com.jacobmountain.graphql.client.visitor.ClientDetailsVisitor;
import com.jacobmountain.graphql.client.visitor.MethodDetails;
import com.jacobmountain.graphql.client.visitor.MethodDetailsVisitor;
import com.squareup.javapoet.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ClientGenerator generates the implementation of the interface annotated with @GraphQLClient
 */
@Slf4j
@RequiredArgsConstructor
public class ClientGenerator {

    private final Filer filer;

    private final TypeMapper typeMapper;

    private final String packageName;

    private final Schema schema;

    private final AbstractStage arguments;

    private final AbstractStage query;

    private final AbstractStage returnResults;

    public ClientGenerator(Filer filer, TypeMapper typeMapper, String packageName, String dtoPackageName, Schema schema, boolean reactive) {
        this.filer = filer;
        this.typeMapper = typeMapper;
        this.packageName = packageName;
        this.schema = schema;
        this.arguments = new ArgumentAssemblyStage();
        QueryGenerator queryGenerator = new QueryGenerator(schema);
        if (reactive) {
            this.query = new ReactiveQueryStage(queryGenerator, schema, typeMapper, dtoPackageName);
            this.returnResults = new ReactiveReturnStage(schema, typeMapper);
        } else {
            this.query = new BlockingQueryStage(queryGenerator, schema, typeMapper, dtoPackageName);
            this.returnResults = new OptionalReturnStage(schema, typeMapper);
        }
    }

    /**
     * Generates the implementation of the @GraphQLClient interface
     *
     * @param element the Element that has the @GraphQLClient on
     * @param suffix  the implementations suffix
     */
    @SneakyThrows
    public void generate(Element element, String suffix) {
        if (StringUtils.isEmpty(suffix)) {
            throw new IllegalArgumentException("Invalid suffix for implementation of client: " + element.getSimpleName());
        }
        ClientDetails details = element.accept(new ClientDetailsVisitor(), null);
        // Generate the class
        TypeSpec.Builder builder = TypeSpec.classBuilder(element.getSimpleName() + suffix)
                .addSuperinterface(ClassName.get((TypeElement) element))
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationUtils.generated());
        // Add type argument to the client
        Stream.of(arguments, query, returnResults)
                .flatMap(it -> it.getTypeArguments().stream())
                .map(TypeVariableName::get)
                .forEach(builder::addTypeVariable);
        // Add any necessary member variables to the client
        List<AbstractStage.MemberVariable> memberVariables = Stream.of(arguments, query, returnResults)
                .map(it -> it.getMemberVariables(details))
                .flatMap(Collection::stream)
                .peek(memberVariable -> builder.addField(memberVariable.getType(), memberVariable.getName(), Modifier.PRIVATE, Modifier.FINAL))
                .collect(Collectors.toList());
        // generate the constructor
        builder.addMethod(generateConstructor(memberVariables));

        // for each method on the interface, generate its implementation
        element.getEnclosedElements()
                .forEach(el -> generateImpl(builder, el, details));

        writeToFile(builder.build());
    }

    /**
     * Generates a constructor which takes in any required member variables (usually the fetcher)
     *
     * @param variables the required member variables
     */
    private MethodSpec generateConstructor(List<AbstractStage.MemberVariable> variables) {
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        variables.forEach(var -> constructor.addParameter(var.getType(), var.getName())
                .addStatement("this.$L = $L", var.getName(), var.getName()));
        return constructor.build();
    }

    /**
     * Generates the client implementation of one method of the interface
     *
     * @param method the method of the @GraphQLClient annotated interface
     */
    private void generateImpl(TypeSpec.Builder clazz, Element method, ClientDetails client) {
        log.info("");
        MethodDetails details = method.accept(new MethodDetailsVisitor(schema), typeMapper);
        log.info("{}", details);

        generateArgumentDTO(details)
                .ifPresent(clazz::addType);

        MethodSpec.Builder builder = MethodSpec.methodBuilder(method.getSimpleName().toString())
                .returns(details.getReturnType())
                .addModifiers(Modifier.PUBLIC)
                .addParameters(details.getParameterSpec());

        this.arguments.assemble(client, details).forEach(builder::addStatement);
        this.query.assemble(client, details).forEach(builder::addStatement);
        this.returnResults.assemble(client, details).forEach(builder::addStatement);

        clazz.addMethod(builder.build());
    }

    public Optional<TypeSpec> generateArgumentDTO(MethodDetails details) {
        return Optional.of(details)
                .filter(MethodDetails::hasParameters)
                .map(it -> {
                    String name = details.getArgumentClassname();
                    PojoBuilder builder = PojoBuilder.newType(name, packageName).staTic();
                    details.getParameters()
                            .forEach(variable -> {
                                String field = variable.getName();
                                if (variable.getAnnotation() != null) {
                                    field = variable.getAnnotation().value();
                                }
                                builder.withField(variable.getType(), field);
                            });
                    return builder.buildClass();
                });
    }

    private void writeToFile(TypeSpec spec) throws Exception {
        JavaFile.builder(packageName, spec)
                .indent("\t")
                .skipJavaLangImports(true)
                .build()
                .writeTo(filer);
    }

}

