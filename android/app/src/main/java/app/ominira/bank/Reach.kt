package app.ominira.bank

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

// Pure Kotlin: no android.* imports, so the JVM tests can check it.
object Reach {

    // Only a failed connection is unreachable. A reply the Gateway sent (401, 404, 429, 5xx) is not.
    fun unreachable(error: Throwable): Boolean {
        var e: Throwable? = error
        var depth = 0
        while (e != null && depth < 8) {
            when (e) {
                is UnknownHostException, is ConnectException, is NoRouteToHostException,
                is PortUnreachableException, is SocketTimeoutException, is SSLException -> return true
            }
            e = e.cause
            depth += 1
        }
        return false
    }
}
