package com.annimon.favisbot

import com.annimon.favisbot.db.*
import com.google.inject.Guice
import io.mockk.*
import kong.unirest.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

class ServerTest {

    companion object {
        private val injector = Guice.createInjector(TestModule())
        private const val baseUrl = "http://127.0.0.1:9377"
        private val server = injector.getInstance(Server::class.java)

        @BeforeClass
        @JvmStatic
        fun setUp() {
            server.start()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            server.stop()
        }
    }

    private val itemsRepository = injector.getInstance(ItemsRepository::class.java)
    private val usersRepository = injector.getInstance(UsersRepository::class.java)
    private val userSetsRepository = injector.getInstance(UserSetsRepository::class.java)

    @Test
    fun `GET meta default appName and user`() {
        val guid = "abc1230f-abcd"
        val response = Unirest.get("$baseUrl/meta/$guid").asObject(HashMap::class.java)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body["appName"]).isEqualTo("appName")
        assertThat(response.body["user"]).isEqualTo("")
    }

    @Test
    fun `GET meta valid user`() {
        val guid = "abc1230f-abcd"
        val user = createDefaultUser(guid)
        mockkObject(usersRepository)
        every { usersRepository.findUserByGUID(guid) } returns user
        val response = Unirest.get("$baseUrl/meta/$guid").asObject(HashMap::class.java)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body["appName"]).isEqualTo("appName")
        assertThat(response.body["user"]).isEqualTo("name")
        assertThat(response.body["stickerSets"] as List<*>)
            .contains("!animation", "!document", "!gif", "!photo", "!video")
        unmockkAll()
    }

    @Test
    fun `GET meta valid user with sticker sets`() {
        val guid = "abc1230f-abcd"
        val user = createDefaultUser(guid)
        mockkObject(usersRepository, userSetsRepository)
        every { usersRepository.findUserByGUID(guid) } returns user
        every {
            userSetsRepository.findAllUserSets(user.id)
        } returns listOf("set1", "set2")
        val response = Unirest.get("$baseUrl/meta/$guid").asObject(HashMap::class.java)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body["stickerSets"] as List<*>).contains("set1", "set2")
        unmockkAll()
    }

    @Test
    fun `GET items in set empty guid`() {
        val response = Unirest.get("$baseUrl/items/sample-set").asString()
        assertThat(response.status).isEqualTo(401)
    }

    @Test
    fun `GET items in set with guid`() {
        val guid = "abc1230f-abcd"
        val user = createDefaultUser(guid)
        val el1 = DbItemWithTag().apply {
            id = "1"
            stickerSet = "sample-set"
        }
        val el2 = DbItemWithTag().apply {
            id = "2"
            stickerSet = "sample-set"
        }
        mockkObject(usersRepository, userSetsRepository)
        every { usersRepository.findUserByGUID(guid) } returns user
        every {
            userSetsRepository.findAllByStickerSet(119, "sample-set")
        } returns listOf(el1, el2)
        val response = Unirest.get("$baseUrl/items/sample-set")
            .header("guid", guid)
            .asObject(Array<DbItemWithTag>::class.java)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).contains(el1, el2)
        unmockkAll()
    }

    @Test
    fun `POST items add tag`() {
        val guid = "abc1230f-abcd"
        val user = createDefaultUser(guid)
        mockkObject(itemsRepository, usersRepository)
        every { usersRepository.findUserByGUID(guid) } returns user
        val response = Unirest.post("$baseUrl/items")
            .header("guid", guid)
            .body(Server.BodyItem("1234", "tag1,tag2"))
            .asEmpty()

        val userTag = DbUserTag("1234", user.id, "tag1,tag2")
        assertThat(response.status).isEqualTo(201)
        verify { itemsRepository.replaceUserTags(userTag) }
        excludeRecords {
            itemsRepository.isItemTagged(userTag)
            itemsRepository.removeUserTagIfExists(userTag)
        }
        confirmVerified(itemsRepository)
        unmockkAll()
    }

    @Test
    fun `POST items update tag`() {
        val guid = "abc1230f-abcd"
        val user = createDefaultUser(guid)
        mockkObject(itemsRepository, usersRepository)
        every { usersRepository.findUserByGUID(guid) } returns user
        every { itemsRepository.isItemTagged(any()) } returns true
        val response = Unirest.post("$baseUrl/items")
            .header("guid", guid)
            .body(Server.BodyItem("1234", "tag1,tag2"))
            .asEmpty()

        val userTag = DbUserTag("1234", user.id, "tag1,tag2")
        assertThat(response.status).isEqualTo(200)
        verify { itemsRepository.replaceUserTags(userTag) }
        excludeRecords {
            itemsRepository.isItemTagged(userTag)
            itemsRepository.removeUserTagIfExists(userTag)
        }
        confirmVerified(itemsRepository)
        unmockkAll()
    }

    @Test
    fun `POST items remove tag`() {
        val guid = "abc1230f-abcd"
        val user = createDefaultUser(guid)
        mockkObject(itemsRepository, usersRepository)
        every { usersRepository.findUserByGUID(guid) } returns user
        every { itemsRepository.isItemTagged(any()) } returns true
        val response = Unirest.post("$baseUrl/items")
            .header("guid", guid)
            .body(Server.BodyItem("1234", ""))
            .asEmpty()

        val userTag = DbUserTag("1234", user.id, "")
        assertThat(response.status).isEqualTo(205)
        verify(exactly = 0) { itemsRepository.replaceUserTags(userTag) }
        verify { itemsRepository.removeUserTagIfExists(userTag) }
        excludeRecords {
            itemsRepository.isItemTagged(userTag)
        }
        confirmVerified(itemsRepository)
        unmockkAll()
    }

    @Test
    fun `POST items remove not existing tag`() {
        val guid = "abc1230f-abcd"
        val user = createDefaultUser(guid)
        mockkObject(itemsRepository, usersRepository)
        every { usersRepository.findUserByGUID(guid) } returns user
        every { itemsRepository.isItemTagged(any()) } returns false
        val response = Unirest.post("$baseUrl/items")
            .header("guid", guid)
            .body(Server.BodyItem("1234", ""))
            .asEmpty()

        val userTag = DbUserTag("1234", user.id, "")
        assertThat(response.status).isEqualTo(204)
        verify(exactly = 0) { itemsRepository.replaceUserTags(userTag) }
        verify { itemsRepository.removeUserTagIfExists(userTag) }
        excludeRecords {
            itemsRepository.isItemTagged(userTag)
        }
        confirmVerified(itemsRepository)
        unmockkAll()
    }

    private fun createDefaultUser(guid: String) =
        DbUser().apply {
            id = 119
            firstName = "name"
            this.guid = guid
        }
}
