package me.rerere.locallm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImageProfileTest {

    @Test
    fun createProfile_storesAndRetrieves() {
        val store = ImageProfileStore()
        val profile = ImageProfile(
            id = "p1",
            name = "Fast SDXL",
            modelId = "m1",
            width = 512,
            height = 512,
            steps = 10,
            cfgScale = 5.0f,
        )
        store.save(profile)
        assertEquals(profile, store.getById("p1"))
    }

    @Test
    fun updateProfile_modifiesInPlace() {
        val store = ImageProfileStore()
        store.save(ImageProfile(id = "p1", name = "Old", modelId = "m1"))
        store.save(ImageProfile(id = "p1", name = "Updated", modelId = "m1"))
        assertEquals("Updated", store.getById("p1")?.name)
    }

    @Test
    fun deleteProfile_removesEntry() {
        val store = ImageProfileStore()
        store.save(ImageProfile(id = "p1", name = "P1", modelId = "m1"))
        store.save(ImageProfile(id = "p2", name = "P2", modelId = "m1"))
        store.delete("p1")
        assertNull(store.getById("p1"))
        assertNotNull(store.getById("p2"))
    }

    @Test
    fun listByModelId_returnsProfilesForAModel() {
        val store = ImageProfileStore()
        store.save(ImageProfile(id = "p1", name = "P1", modelId = "m1"))
        store.save(ImageProfile(id = "p2", name = "P2", modelId = "m1"))
        store.save(ImageProfile(id = "p3", name = "P3", modelId = "m2"))
        assertEquals(2, store.listByModelId("m1").size)
        assertEquals(1, store.listByModelId("m2").size)
    }
}
