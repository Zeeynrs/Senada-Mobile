package senada.app

import kotlin.test.Test
import kotlin.test.assertEquals

class UserProfileStoreTest {
    @Test
    fun profileRoundTripsThroughSettings() {
        var json: String? = null
        val store = UserProfileStore({ json }, { json = it })
        val profile = UserProfile("Adit", "profile/avatar.jpg", 1_795_219_200_000L)

        store.save(profile)

        assertEquals(profile, store.load())
        store.save(profile.copy(photoPath = null))
        assertEquals(profile.copy(photoPath = null), store.load())
    }

    @Test
    fun controllerRecoversFromInvalidSettingsAndPersistsCreationDate() {
        for (broken in listOf("{", "[]", """{"version":1,"name":42,"photoPath":null,"createdAt":1}""")) {
            var json: String? = broken
            val store = UserProfileStore({ json }, { json = it })

            val controller = UserProfileController(store) { 123L }

            assertEquals(UserProfile("", null, 123L), controller.state.value)
            assertEquals(controller.state.value, UserProfileController(store) { 456L }.state.value)
        }
    }

    @Test
    fun controllerTrimsNameBeforePublishingAndSaving() {
        var json: String? = null
        val store = UserProfileStore({ json }, { json = it })
        val controller = UserProfileController(store) { 123L }

        controller.update("  ${"1234567890".repeat(4)}ABCD  ", null)

        assertEquals("1234567890123456789012345678901234567890", controller.state.value.name)
        assertEquals(controller.state.value, store.load())
        assertEquals(123L, controller.state.value.createdAt)
    }

    @Test
    fun trimmingPreservesSupplementaryCharactersAtTheLimit() {
        val prefix = "A".repeat(39)

        assertEquals("${prefix}🎵", normalizeProfileName("  ${prefix}🎵B  "))
    }
}
