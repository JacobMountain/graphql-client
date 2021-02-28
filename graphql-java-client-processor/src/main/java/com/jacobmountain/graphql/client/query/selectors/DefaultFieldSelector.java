package com.jacobmountain.graphql.client.query.selectors;

import com.jacobmountain.graphql.client.query.QueryContext;
import com.jacobmountain.graphql.client.query.QueryGenerator;
import com.jacobmountain.graphql.client.query.filters.FieldFilter;
import com.jacobmountain.graphql.client.utils.OptionalUtils;
import com.jacobmountain.graphql.client.utils.Schema;
import graphql.language.FieldDefinition;
import graphql.language.TypeDefinition;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class DefaultFieldSelector implements FieldSelector {

    private final Schema schema;

    private final QueryGenerator queryGenerator;

    @Override
    public Stream<String> selectFields(TypeDefinition<?> typeDefinition, QueryContext context, List<FieldFilter> filters) {
        return schema.getChildren(typeDefinition)
                .filter(this::filter)
                .map(child -> queryGenerator.generateFieldSelection(
                        child.getName(),
                        context.withType(child).increment(),
                        filters
                ))
                .flatMap(OptionalUtils::toStream);
    }

    protected boolean filter(FieldDefinition fieldDefinition) {
        return true;
    }

}
