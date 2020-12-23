package co.uk.jacobmountain.visitor;

import co.uk.jacobmountain.GraphQLArgument;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Parameter {

    private TypeName type;

    private String name;

    private GraphQLArgument annotation;

    public ParameterSpec toSpec() {
        return ParameterSpec.builder(type, name).build();
    }

}
