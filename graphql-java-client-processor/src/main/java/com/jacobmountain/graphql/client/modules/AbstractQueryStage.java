package com.jacobmountain.graphql.client.modules;

import com.jacobmountain.graphql.client.TypeMapper;
import com.jacobmountain.graphql.client.dto.Response;
import com.jacobmountain.graphql.client.query.QueryGenerator;
import com.jacobmountain.graphql.client.utils.Schema;
import com.jacobmountain.graphql.client.visitor.MethodDetails;
import com.jacobmountain.graphql.client.visitor.Parameter;
import com.squareup.javapoet.*;
import graphql.language.ObjectTypeDefinition;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractQueryStage extends AbstractStage {

    protected final TypeName query;

    protected final TypeName mutation;

    protected final TypeName subscription;

    public AbstractQueryStage(Schema schema, TypeMapper typeMapper, String dtoPackageName) {
        super(schema, typeMapper);
        this.query = ClassName.get(dtoPackageName, schema.getQueryTypeName());
        this.mutation = schema.getMutationTypeName().map(it -> ClassName.get(dtoPackageName, it)).orElse(ClassName.get(Void.class));
        this.subscription = schema.getSubscriptionTypeName().map(it -> ClassName.get(dtoPackageName, it)).orElse(ClassName.get(Void.class));
    }

    @Override
    public List<String> getTypeArguments() {
        return Collections.singletonList("Error");
    }

    protected TypeName getReturnTypeName(MethodDetails details) {
        ObjectTypeDefinition typeDefinition = getTypeDefinition(details);
        return ParameterizedTypeName.get(ClassName.get(Response.class), typeMapper.getType(typeDefinition.getName()), TypeVariableName.get("Error"));
    }

    protected String getMethod(MethodDetails details) {
        String method = "query";
        if (details.isMutation()) {
            method = "mutate";
        } else if (details.isSubscription()) {
            method = "subscribe";
        }
        return method;
    }

    protected CodeBlock generateQueryCode(String request, MethodDetails details) {
        Set<String> params = details.getParameters()
                .stream()
                .map(Parameter::getField)
                .collect(Collectors.toSet());
        QueryGenerator queryGenerator = new QueryGenerator(schema, details.getMaxDepth());
        String query;
        if (details.isQuery()) {
            query = queryGenerator.generateQuery(request, details.getField(), params);
        } else if (details.isMutation()) {
            query = queryGenerator.generateMutation(request, details.getField(), params);
        } else if (details.isSubscription()) {
            query = queryGenerator.generateSubscription(request, details.getField(), params);
        } else {
            throw new RuntimeException("");
        }
        boolean hasArgs = details.hasParameters();
        return CodeBlock.of(
                "(\"$L\", $L)", query, hasArgs ? "args" : "null"
        );
    }

}