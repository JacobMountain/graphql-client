package co.uk.jacobmountain

import co.uk.jacobmountain.service.ResultService
import co.uk.jacobmountain.util.Assert
import co.uk.jacobmountain.util.RandomResultUtil
import org.spockframework.spring.SpringBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import spock.lang.Specification
import spock.lang.Subject

@SpringBootTest(
        classes = ExampleApplication,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ClientSpec extends Specification {

    @LocalServerPort
    int port

    @Subject
    MatchResultsClient client

    @SpringBean
    ResultService mock = Mock(ResultService)

    SpyFetcher fetcher

    def setup() {
        fetcher = new SpyFetcher(new ExampleFetcher("http://localhost:$port/graph"))
        client = new MatchResultsClientGraph(fetcher)
    }

    def "I can get a query with an argument"() {
        given:
        def expected = RandomResultUtil.randomResult()
        mock.getResult(1) >> expected

        when:
        def result = client.getResult(1)

        then:
        result != null
        Assert.assertEquals(expected, result)
    }

    def "I can get a query a list"() {
        given:
        def expected = (1..3).collect { RandomResultUtil.randomResult() }
        mock.getResults() >> expected

        when:
        def result = client.getResults()

        then:
        result != null
        Assert.assertEquals(expected, result)
    }

    def "I can get a query and wrap it in an Optional"() {
        given:
        def expected = RandomResultUtil.randomResult()
        mock.getResult(2) >> expected

        when:
        def result = client.getResultOptional(2)

        then:
        result != null
        Assert.assertEquals(expected, result.orElse(null))
    }

    def "I can get a query with a parameter named differently to its argument"() {
        given:
        def expected = RandomResultUtil.randomResult()
        mock.getResult(3) >> expected

        when:
        def result = client.getResultWithRenamedArg(3)

        then:
        Assert.assertEquals(expected, result)
    }


    def "I can query with non-null arguments"() {
        given:
        def expected = RandomResultUtil.randomTeam()
        mock.getTeam("arsenal") >> expected

        when:
        def team = client.getTeam("arsenal")

        then:
        Assert.assertEquals(expected, team)
    }


    def "A NPE is thrown for null arguments before the request is made"() {
        when:
        client.getTeam(null)

        then: "No HTTP requests should occur"
        !fetcher.hasInteractions()

        and: "A NPE should be thrown"
        thrown(NullPointerException)
    }


}
