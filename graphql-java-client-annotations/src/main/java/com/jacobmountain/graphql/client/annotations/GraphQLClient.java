package com.jacobmountain.graphql.client.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GraphQLClient {

    String schema();

    Scalar[] mapping() default {};

    boolean nullChecking() default false;

    String implSuffix() default "Graph";

    boolean reactive() default false;

    String dtoPackage() default "dto";

    @interface Scalar {

        String from();

        Class<?> to();

    }

}
