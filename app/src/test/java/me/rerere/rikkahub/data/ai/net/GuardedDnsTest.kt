package me.rerere.rikkahub.data.ai.net

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class GuardedDnsTest {
    private fun fixedDns(vararg ips: String) = Dns { _ ->
        ips.map { InetAddress.getByName(it) }
    }

    @Test
    fun `public address resolves normally`() {
        val dns = GuardedDns(fixedDns("93.184.216.34"), allowPrivate = false)

        assertEquals(1, dns.lookup("example.com").size)
    }

    @Test
    fun `loopback is blocked`() {
        val dns = GuardedDns(fixedDns("127.0.0.1"), allowPrivate = false)

        try {
            dns.lookup("localhost")
            fail("expected UnknownHostException")
        } catch (error: UnknownHostException) {
            assertTrue(error.message!!.contains("blocked_private_address"))
        }
    }

    @Test
    fun `private ranges and metadata address are blocked`() {
        listOf(
            "10.0.0.5",
            "192.168.1.10",
            "172.16.0.3",
            "100.64.0.1",
            "169.254.169.254",
        ).forEach { ip ->
            val dns = GuardedDns(fixedDns(ip), allowPrivate = false)
            try {
                dns.lookup("target.example")
                fail("expected UnknownHostException for $ip")
            } catch (error: UnknownHostException) {
                assertTrue(error.message!!.contains("blocked_private_address"))
            }
        }
    }

    @Test
    fun `ipv6 loopback link local and unique local are blocked`() {
        listOf("::1", "fe80::1", "fd00::1", "fc00::1").forEach { ip ->
            val dns = GuardedDns(fixedDns(ip), allowPrivate = false)
            try {
                dns.lookup("target.example")
                fail("expected UnknownHostException for $ip")
            } catch (error: UnknownHostException) {
                assertTrue(error.message!!.contains("blocked_private_address"))
            }
        }
    }

    @Test
    fun `mixed public and private answer set is rejected`() {
        val dns = GuardedDns(
            fixedDns("93.184.216.34", "127.0.0.1"),
            allowPrivate = false,
        )

        try {
            dns.lookup("mixed.example")
            fail("expected UnknownHostException")
        } catch (error: UnknownHostException) {
            assertTrue(error.message!!.contains("blocked_private_address"))
        }
    }

    @Test
    fun `literal checker blocks private addresses and permits public hosts`() {
        listOf(
            "127.0.0.1",
            "192.168.1.1",
            "100.64.0.1",
            "169.254.169.254",
            "::1",
            "fd00::1",
        ).forEach { host ->
            assertTrue("$host should be blocked", hostIsBlockedLiteral(host))
        }

        listOf(
            "8.8.8.8",
            "2001:4860:4860::8888",
            "example.com",
            "html.duckduckgo.com",
        ).forEach { host ->
            assertFalse("$host should be allowed", hostIsBlockedLiteral(host))
        }
    }

    @Test
    fun `allowPrivate opt in permits private ranges`() {
        val dns = GuardedDns(fixedDns("192.168.1.10"), allowPrivate = true)

        assertEquals(1, dns.lookup("router.local").size)
    }
}
