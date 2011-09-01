package betamax.proxy

import betamax.util.SimpleServer
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.http.HttpHost
import org.apache.http.impl.conn.ProxySelectorRoutePlanner
import org.junit.Rule
import groovyx.net.http.*
import static groovyx.net.http.ContentType.URLENC
import static java.net.HttpURLConnection.HTTP_OK
import static org.apache.http.HttpHeaders.VIA
import static org.apache.http.conn.params.ConnRoutePNames.DEFAULT_PROXY
import spock.lang.*
import betamax.Recorder
import betamax.Betamax
import betamax.util.EchoHandler

class ProxyNetworkCommsSpec extends Specification {

    @AutoCleanup("deleteDir") File tapeRoot = new File(System.properties."java.io.tmpdir", "tapes")
    @Rule Recorder recorder = new Recorder(tapeRoot: tapeRoot)
    @Shared @AutoCleanup("stop") SimpleServer endpoint = new SimpleServer()

    def setupSpec() {
        endpoint.start(EchoHandler)
    }

    @Timeout(10)
	@Betamax(tape = "proxy network comms spec")
    def "proxy intercepts URL connections"() {
        given:
        HttpURLConnection connection = new URL(endpoint.url).openConnection()
        connection.connect()

        expect:
        connection.responseCode == HTTP_OK
        connection.getHeaderField(VIA) == "Betamax"

        cleanup:
        connection.disconnect()
    }

    @Timeout(10)
	@Betamax(tape = "proxy network comms spec")
    def "proxy intercepts HTTPClient connections when using ProxySelectorRoutePlanner"() {
        given:
        def http = new RESTClient(endpoint.url)
        def routePlanner = new ProxySelectorRoutePlanner(http.client.connectionManager.schemeRegistry, ProxySelector.default)
        http.client.routePlanner = routePlanner

        when:
        def response = http.get(path: "/")

        then:
        response.status == HTTP_OK
        response.getFirstHeader(VIA)?.value == "Betamax"
    }

    @Timeout(10)
	@Betamax(tape = "proxy network comms spec")
    def "proxy intercepts HTTPClient connections when explicitly told to"() {
        given:
        def http = new RESTClient(endpoint.url)
        http.client.params.setParameter(DEFAULT_PROXY, new HttpHost("localhost", recorder.proxyPort, "http"))

        when:
        def response = http.get(path: "/")

        then:
        response.status == HTTP_OK
        response.getFirstHeader(VIA)?.value == "Betamax"
    }

    @Timeout(10)
	@Betamax(tape = "proxy network comms spec")
    def "proxy intercepts HttpURLClient connections"() {
        given:
        def http = new HttpURLClient(url: endpoint.url)

        when:
        def response = http.request(path: "/")

        then:
        response.status == HTTP_OK
        response.getFirstHeader(VIA)?.value == "Betamax"
    }

    @Ignore
    @Timeout(10)
	@Betamax(tape = "proxy network comms spec")
    def "proxy intercepts HTTPClient 3.x connections"() {
        given:
        def client = new HttpClient()
        def request = new GetMethod(endpoint.url)

        when:
        def status = client.executeMethod(request)

        then:
        status == HTTP_OK
        request.getResponseHeader(VIA)?.value == "Betamax"
    }

    @Timeout(10)
	@Betamax(tape = "proxy network comms spec")
    @Unroll("proxy handles #method requests")
    def "proxy handles all request methods"() {
        given:
        def http = new RESTClient(endpoint.url)
        def routePlanner = new ProxySelectorRoutePlanner(http.client.connectionManager.schemeRegistry, ProxySelector.default)
        http.client.routePlanner = routePlanner

        when:
        def response = http."$method"(path: "/")

        then:
        response.status == HTTP_OK
        response.getFirstHeader(VIA)?.value == "Betamax"

        cleanup:
        http.shutdown()

        where:
        method << ["get", "post", "put", "head", "delete", "options"]
    }

    @Timeout(10)
	@Betamax(tape = "proxy network comms spec")
    def "proxy forwards query string"() {
        given:
        def http = new RESTClient(endpoint.url)
        def routePlanner = new ProxySelectorRoutePlanner(http.client.connectionManager.schemeRegistry, ProxySelector.default)
        http.client.routePlanner = routePlanner

        when:
        def response = http.get(path: "/", query: [q: "1"])

        then:
        response.status == HTTP_OK
        response.data.text.contains("GET /?q=1 HTTP/1.1")

        cleanup:
        http.shutdown()
    }

    @Timeout(10)
	@Betamax(tape = "proxy network comms spec")
    def "proxy forwards post data"() {
        given:
        def http = new RESTClient(endpoint.url)
        def routePlanner = new ProxySelectorRoutePlanner(http.client.connectionManager.schemeRegistry, ProxySelector.default)
        http.client.routePlanner = routePlanner

        when:
        def response = http.post(path: "/", body: [q: "1"], requestContentType: URLENC)

        then:
        response.status == HTTP_OK
        response.data.text.endsWith("\nq=1")

        cleanup:
        http.shutdown()
    }

}
