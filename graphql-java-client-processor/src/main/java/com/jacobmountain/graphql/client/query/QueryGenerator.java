package com.jacobmountain.graphql.client.query;

import com.jacobmountain.graphql.client.exceptions.FieldNotFoundException;
import com.jacobmountain.graphql.client.query.filters.*;
import com.jacobmountain.graphql.client.query.selectors.DefaultFieldSelector;
import com.jacobmountain.graphql.client.query.selectors.DelegatingFieldSelector;
import com.jacobmountain.graphql.client.query.selectors.InlineFragmentRenderer;
import com.jacobmountain.graphql.client.utils.Schema;
import com.jacobmountain.graphql.client.utils.StringUtils;
import com.jacobmountain.graphql.client.visitor.GraphQLFieldSelection;
import graphql.language.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class QueryGenerator {

    private final Schema schema;

    public QueryGenerator(Schema registry) {
        this.schema = registry;
    }

    public QueryBuilder query() {
        return new QueryBuilder("query");
    }

    public QueryBuilder mutation() {
        return new QueryBuilder("mutation");
    }

    public QueryBuilder subscription() {
        return new QueryBuilder("subscription");
    }

    private String doGenerateQuery(String request, String field, String type, Set<String> params, List<FieldFilter> filters) {
        FieldDefinition definition = schema.findField(field).orElseThrow(FieldNotFoundException.create(field));

        Set<String> args = new HashSet<>();

        final QueryContext root = new QueryContext(null, 0, definition, params);
        String inner = generateFieldSelection(field, root, args, filters)
                .orElseThrow(RuntimeException::new);

        String collect = String.join(", ", args);

        if (!args.isEmpty()) {
            collect = "(" + collect + ")";
        }

        return generateQueryName(request, type, field) + collect + " { " + inner + " }";
    }

    private String generateQueryName(String request, String type, String field) {
        if (StringUtils.isEmpty(request)) {
            request = StringUtils.capitalize(field);
        }
        return type + " " + request;
    }

    public Optional<String> generateFieldSelection(String alias,
                                                   QueryContext context,
                                                   Set<String> argumentCollector,
                                                   List<FieldFilter> filters) {
        String type = Schema.unwrap(context.getFieldDefinition().getType());
        TypeDefinition<?> typeDefinition = schema.getTypeDefinition(type).orElse(null);

        if (!filters.stream().allMatch(fi -> fi.shouldAddField(context))) {
            return Optional.empty();
        }

        String args = generateFieldArgs(context.getFieldDefinition(), context.getParams(), argumentCollector);
        if (Objects.isNull(typeDefinition) || typeDefinition.getChildren().isEmpty() || typeDefinition instanceof EnumTypeDefinition) {
            return Optional.of(alias + args);
        }

        return new DelegatingFieldSelector(
                new DefaultFieldSelector(schema, this),
                new InlineFragmentRenderer(schema, this)
        )
                .selectFields(typeDefinition, context, argumentCollector, filters)
                .map(children -> alias + args + " " + children)
                .findFirst();
    }

    public class QueryBuilder {

        private final String type;

        private final List<FieldFilter> filters = new ArrayList<>();

        QueryBuilder(String type) {
            this.type = type;
        }

        public QueryBuilder maxDepth(int maxDepth) {
            this.filters.add(new MaxDepthFieldFilter(maxDepth));
            return this;
        }

        public QueryBuilder select(List<GraphQLFieldSelection> selections) {
            this.filters.add(new SelectionFieldFilter(selections));
            return this;
        }

        public String build(String request, String field, Set<String> params) {
            this.filters.add(new AllNonNullArgsFieldFilter());
            this.filters.add(new FieldDuplicationFilter());
            return doGenerateQuery(request, field, type, params, filters);
        }
    }

    private String generateFieldArgs(FieldDefinition field, Set<String> params, Set<String> argsCollector) {
        List<InputValueDefinition> args = field.getInputValueDefinitions();
        Set<String> finalParams = new HashSet<>(params);
        String collect = args.stream()
                .filter(o -> finalParams.remove(o.getName()))
                .peek(arg -> {
                    boolean nonNull = arg.getType() instanceof NonNullType;
                    String type = Schema.unwrap(arg.getType());
                    argsCollector.add(
                            "$" + arg.getName() + ": " + type + (nonNull ? "!" : "")
                    );
                })
                .map(arg -> arg.getName() + ": $" + arg.getName())
                .collect(Collectors.joining(", "));
        if (StringUtils.isEmpty(collect)) {
            return "";
        }
        return "(" + collect + ")";
    }

}
