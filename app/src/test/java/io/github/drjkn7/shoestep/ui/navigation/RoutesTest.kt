package io.github.drjkn7.shoestep.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {

    private val uuid = "3f2b8c1e-4d5a-4b6c-8d7e-9f0a1b2c3d4e"

    @Test fun buildsDetailRoute() = assertEquals("shoe/$uuid", Routes.shoeDetail(uuid))

    @Test fun buildsEditRoute() = assertEquals("shoe/$uuid/edit", Routes.editShoe(uuid))

    @Test fun buildsAddPastSessionRoute() = assertEquals("shoe/$uuid/add-session", Routes.addPastSession(uuid))

    @Test(expected = IllegalArgumentException::class)
    fun addPastSessionRejectsUnsafeIds() {
        Routes.addPastSession("abc/../settings")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSlash() {
        Routes.shoeDetail("abc/../settings")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsQueryCharacters() {
        Routes.shoeDetail("abc?x=1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmpty() {
        Routes.editShoe("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOverlong() {
        Routes.shoeDetail("a".repeat(65))
    }
}
