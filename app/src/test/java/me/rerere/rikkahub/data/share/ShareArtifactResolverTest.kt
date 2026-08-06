package me.rerere.rikkahub.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareArtifactResolverTest {

    @Test
    fun `plain gallery id resolves`() {
        assertEquals(1, ShareArtifactResolver.resolveArtifactId("1"))
    }

    @Test
    fun `img prefixed id resolves`() {
        assertEquals(42, ShareArtifactResolver.resolveArtifactId("img_42"))
    }

    @Test
    fun `whitespace trimmed id resolves`() {
        assertEquals(7, ShareArtifactResolver.resolveArtifactId("  img_7  "))
    }

    @Test
    fun `non numeric reference is rejected`() {
        assertNull(ShareArtifactResolver.resolveArtifactId("abc"))
    }

    @Test
    fun `zero or negative id is rejected`() {
        assertNull(ShareArtifactResolver.resolveArtifactId("0"))
        assertNull(ShareArtifactResolver.resolveArtifactId("-3"))
    }

    @Test
    fun `empty reference is rejected`() {
        assertNull(ShareArtifactResolver.resolveArtifactId(""))
        assertNull(ShareArtifactResolver.resolveArtifactId("img_"))
    }
}
