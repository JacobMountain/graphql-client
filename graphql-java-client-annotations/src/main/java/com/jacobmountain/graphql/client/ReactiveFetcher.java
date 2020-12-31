package com.jacobmountain.graphql.client;

import com.jacobmountain.graphql.client.dto.Response;
import org.reactivestreams.Publisher;

public interface ReactiveFetcher<Q, M, Error> {

    <A> Publisher<Response<Q, Error>> query(String query, A args);

    <A> Publisher<Response<M, Error>> mutate(String mutation, A args);

}
