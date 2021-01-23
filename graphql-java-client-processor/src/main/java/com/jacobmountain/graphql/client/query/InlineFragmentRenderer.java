package com.jacobmountain.graphql.client.query;

import com.jacobmountain.graphql.client.utils.Schema;
import graphql.language.FieldDefinition;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class InlineFragmentRenderer implements FieldSelector {

    private final Schema schema;

    private final QueryGenerator queryGenerator;

    @Override
    public Stream<String> selectFields(TypeDefinition<?> typeDefinition, QueryContext context, FragmentRenderer fragmentRenderer, Set<String> argumentCollector, List<FieldFilter> filters) {
        return schema.getTypesImplementing(typeDefinition)
                .map(interfac -> queryGenerator.generateFieldSelection(
                        interfac,
                        context.withType(new FieldDefinition(interfac, new TypeName(interfac))),
                        fragmentRenderer,
                        argumentCollector,
                        filters
                ))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(query -> "... on " + query);
    }
}
