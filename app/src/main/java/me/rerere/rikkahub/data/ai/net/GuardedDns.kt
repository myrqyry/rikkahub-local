package me.rerere.rikkahub.data.ai.net

import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * True when an address must not be reached by an agent-controlled HTTP request.
 *
 * This covers loopback, link-local, site-local/private, any-local, multicast, CGNAT
 * (100.64.0.0/10), and IPv6 unique-local addresses (fc00::/7). The cloud metadata address
 * 169.254.169.254 is covered by the link-local check.
 */
internal fun InetAddress.isBlockedTarget(): Boolean {
    if (
        isLoopbackAddress ||
        isAnyLocalAddress ||
        isLinkLocalAddress ||
        isSiteLocalAddress ||
        isMulticastAddress
    ) {
        return true
    }

    if (this is Inet4Address) {
        val bytes = address
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        if (first == 100 && second in 64..127) return true
    }

    if (this is Inet6Address) {
        val first = address[0].toInt() and 0xFF
        if (first and 0xFE == 0xFC) return true
    }

    return false
}

/**
 * DNS wrapper that rejects a hostname when any resolved address is private or otherwise unsafe.
 *
 * Every redirect made by OkHttp uses the same DNS instance, so a public URL cannot redirect into
 * localhost or the user's LAN. Mixed public/private answer sets are rejected entirely rather than
 * allowing a hostile resolver to smuggle one private address alongside a public address.
 */
class GuardedDns(
    private val delegate: Dns = Dns.SYSTEM,
    private val allowPrivate: Boolean = false,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (allowPrivate) return addresses

        val blocked = addresses.firstOrNull { it.isBlockedTarget() }
        if (blocked != null) {
            throw UnknownHostException(
                "blocked_private_address: $hostname resolves to ${blocked.hostAddress}",
            )
        }
        return addresses
    }
}

private val IPV4_LITERAL_RE = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

/**
 * Checks IP literals before OkHttp dials them. OkHttp does not consult custom DNS for literal IP
 * hosts, so DNS filtering alone would leave a direct `http://127.0.0.1` escape hatch.
 */
internal fun hostIsBlockedLiteral(host: String): Boolean {
    val isLiteral = IPV4_LITERAL_RE.matches(host) || host.contains(':')
    if (!isLiteral) return false

    return try {
        InetAddress.getByName(host).isBlockedTarget()
    } catch (_: UnknownHostException) {
        true
    }
}

/**
 * Returns a client for agent-controlled HTTP requests.
 *
 * The DNS wrapper covers hostname resolution on every redirect. The network interceptor covers
 * literal-IP redirects. `callTimeout` bounds the complete blocking call, including a server that
 * sends bytes slowly forever; a coroutine timeout around `execute()` cannot interrupt that by
 * itself because the blocking call has no suspension point.
 */
internal fun OkHttpClient.withEgressGuard(allowPrivate: Boolean = false): OkHttpClient =
    newBuilder()
        .dns(GuardedDns(Dns.SYSTEM, allowPrivate))
        .callTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val host = chain.request().url.host
            if (!allowPrivate && hostIsBlockedLiteral(host)) {
                throw UnknownHostException("blocked_private_address: $host")
            }
            chain.proceed(chain.request())
        }
        .build()
