package co.uk.jacobmountain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface GraphQLSubscription {

    String value();

    String request() default "";

    boolean mutation() default false;

}
