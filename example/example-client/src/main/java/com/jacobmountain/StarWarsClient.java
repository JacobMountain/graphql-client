package com.jacobmountain;


import com.jacobmountain.dto.Episode;
import com.jacobmountain.dto.LengthUnit;
import com.jacobmountain.dto.Review;
import com.jacobmountain.dto.ReviewInput;
import com.jacobmountain.graphql.client.annotations.*;

import java.util.List;
import java.util.Optional;

@GraphQLClient(
        schema = "Schema.gql",
        nullChecking = true
)
public interface StarWarsClient {

    @GraphQLQuery(value = "hero", name = "HeroByEpisode")
    com.jacobmountain.dto.Character getHero(Episode episode, int first, String after, LengthUnit unit);

    @GraphQLQuery(value = "hero", name = "HeroSummary")
    com.jacobmountain.dto.Character getHero(@GraphQLArgument("hero") String id);

    @GraphQLFragment(type = "Character", name = "HeroSummary", select = {
            @GraphQLField("id"),
            @GraphQLField("name")
    })
    @GraphQLQuery(value = "hero", select = {
            @GraphQLField("id"),
            @GraphQLField("name")
    })
    com.jacobmountain.dto.Character getHeroSummary(String id);

    @GraphQLQuery("hero")
    Optional<com.jacobmountain.dto.Character> getHeroOptional(Episode episode, int first, String after, LengthUnit unit);

    @GraphQLQuery("reviews")
    List<Review> getReviews(Episode episode);

    @GraphQLMutation(value = "createReview")
    Review createReview(Episode episode, @GraphQLArgument("review") ReviewInput input);

}
