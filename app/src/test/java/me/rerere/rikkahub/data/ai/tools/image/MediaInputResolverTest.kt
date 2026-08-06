package me.rerere.rikkahub.data.ai.tools.image

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaInputResolverTest {

    @Test
    fun `reference kind classifies each input form`() {
        assertEquals(ReferenceKind.ARTIFACT, ReferenceClassifier.classify("img_7"))
        assertEquals(ReferenceKind.FILE_URI, ReferenceClassifier.classify("file:///tmp/x.png"))
        assertEquals(ReferenceKind.CONTENT_URI, ReferenceClassifier.classify("content://media/external/images/1"))
        assertEquals(ReferenceKind.ABSOLUTE_PATH, ReferenceClassifier.classify("/data/user/0/x/y.png"))
        assertEquals(ReferenceKind.ABSOLUTE_PATH, ReferenceClassifier.classify("/tmp/x.png"))
        assertEquals(ReferenceKind.UNKNOWN, ReferenceClassifier.classify("not a reference"))
    }

    @Test
    fun `artifact id maps back to gallery id`() {
        assertEquals(7, ReferenceClassifier.artifactToGallery("img_7"))
        assertEquals(null, ReferenceClassifier.artifactToGallery("img_nope"))
    }
}
