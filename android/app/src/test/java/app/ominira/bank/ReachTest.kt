package app.ominira.bank

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ReachTest {

    @Test
    fun aLostConnectionIsUnreachable() {
        assertTrue(Reach.unreachable(UnknownHostException("gateway")))
        assertTrue(Reach.unreachable(ConnectException("refused")))
        assertTrue(Reach.unreachable(SocketTimeoutException("connect timed out")))
        assertTrue(Reach.unreachable(SocketTimeoutException("Read timed out")))
        assertTrue(Reach.unreachable(SSLHandshakeException("bad certificate")))
        assertTrue(Reach.unreachable(IOException("wrapped", SocketTimeoutException("read"))))
    }

    @Test
    fun aGatewayReplyIsNotUnreachable() {
        // GatewayOminiraApi throws a plain IOException for 401, 429 and 5xx replies.
        assertFalse(Reach.unreachable(IOException("HTTP 401 unauthorized")))
        assertFalse(Reach.unreachable(IOException("HTTP 429 rate_limited")))
        assertFalse(Reach.unreachable(IOException("HTTP 503")))
        assertFalse(Reach.unreachable(IllegalStateException("not json")))
    }
}
