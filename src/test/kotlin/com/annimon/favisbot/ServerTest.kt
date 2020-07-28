package com.annimon.favisbot

import com.annimon.favisbot.db.DbUser
import com.annimon.favisbot.db.UsersRepository
import com.google.inject.Guice
import io.mockk.every
import kong.unirest.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ServerTest {

    private val injector = Guice.createInjector(TestModule())
    private val usersRepository = injector.getInstance(UsersRepository::class.java)
    private val server = injector.getInstance(Server::class.java)
    private val baseUrl = "http://127.0.0.1:9377"

    @Test
    fun `GET meta default appName and user`() {
        val guid = "abc1230f-abcd"
        every { usersRepository.findUserByGUID(guid) } returns null
        server.start()
        val response = Unirest.get("$baseUrl/meta/$guid").asObject(HashMap::class.java)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body["appName"]).isEqualTo("appName")
        assertThat(response.body["user"]).isEqualTo("")
        server.stop()
    }

    @Test
    fun `GET meta valid user`() {
        val guid = "abc1230f-abcd"
        val user = DbUser().apply {
            firstName = "name"
            this.guid = guid
        }
        every { usersRepository.findUserByGUID(guid) } returns user
        server.start()
        val response = Unirest.get("$baseUrl/meta/$guid").asObject(HashMap::class.java)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body["appName"]).isEqualTo("appName")
        assertThat(response.body["user"]).isEqualTo("name")
        assertThat(response.body["stickerSets"] as List<*>)
            .contains("!animation", "!document", "!gif", "!photo", "!video")
        server.stop()
    }
}
