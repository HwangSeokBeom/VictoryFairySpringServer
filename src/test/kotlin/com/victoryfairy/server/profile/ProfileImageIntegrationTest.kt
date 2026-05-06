package com.victoryfairy.server.profile

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import javax.imageio.ImageIO
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:profile-image-integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "victory-fairy.profile-image.upload-enabled=true",
        "victory-fairy.profile-image.max-bytes=2097152",
        "victory-fairy.profile-image.max-side=512",
        "victory-fairy.profile-image.upload-dir=build/test-uploads/profile-image-integration",
    ],
)
class ProfileImageIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @BeforeEach
    fun cleanUploads() {
        val dir = Path.of("build/test-uploads/profile-image-integration")
        if (Files.exists(dir)) {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder<Path>())
                .forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun `upload without profile returns PROFILE_REQUIRED`() {
        mockMvc.multipart("/api/v1/me/profile/image") {
            file(jpegFile("image", "profile.jpg"))
            header("X-Device-ID", "profile-image-no-profile-device")
        }
            .andExpect { status { isForbidden() } }
            .andExpect { jsonPath("$.error.code") { value("PROFILE_REQUIRED") } }
            .andExpect { jsonPath("$.error.message") { value("프로필을 먼저 만들어 주세요.") } }
    }

    @Test
    fun `invalid content type is rejected`() {
        val deviceID = "profile-image-invalid-device"
        createProfile(deviceID)

        mockMvc.multipart("/api/v1/me/profile/image") {
            file(MockMultipartFile("image", "profile.txt", MediaType.TEXT_PLAIN_VALUE, "not image".toByteArray()))
            header("X-Device-ID", deviceID)
        }
            .andExpect { status { isBadRequest() } }
            .andExpect { jsonPath("$.error.code") { value("PROFILE_IMAGE_UNSUPPORTED_TYPE") } }
    }

    @Test
    fun `too large upload is rejected`() {
        val deviceID = "profile-image-large-device"
        createProfile(deviceID)

        mockMvc.multipart("/api/v1/me/profile/image") {
            file(MockMultipartFile("image", "profile.jpg", MediaType.IMAGE_JPEG_VALUE, ByteArray(2_097_153)))
            header("X-Device-ID", deviceID)
        }
            .andExpect { status { isPayloadTooLarge() } }
            .andExpect { jsonPath("$.error.code") { value("PROFILE_IMAGE_TOO_LARGE") } }
    }

    @Test
    fun `valid small image upload returns URL and profile update keeps image`() {
        val deviceID = "profile-image-valid-device"
        createProfile(deviceID)

        val firstURL = uploadImage(deviceID, "first.jpg")

        mockMvc.get("/api/v1/me/profile") {
            header("X-Device-ID", deviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.profileImageURL") { value(firstURL) } }
            .andExpect { jsonPath("$.data.profileImageURL") { value(not(containsString("data/uploads"))) } }

        mockMvc.put("/api/v1/me/profile") {
            header("X-Device-ID", deviceID)
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"라팍응원","favoriteTeamID":"doosan-bears","profileEmoji":"🐻"}"""
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.profileImageURL") { value(firstURL) } }

        val secondURL = uploadImage(deviceID, "second.jpg")
        mockMvc.get("/api/v1/me/profile") {
            header("X-Device-ID", deviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.profileImageURL") { value(secondURL) } }
            .andExpect { jsonPath("$.data.profileImageURL") { value(not(firstURL)) } }
    }

    @Test
    fun `delete image clears profile image url`() {
        val deviceID = "profile-image-delete-device"
        createProfile(deviceID)
        uploadImage(deviceID, "delete.jpg")

        mockMvc.delete("/api/v1/me/profile/image") {
            header("X-Device-ID", deviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.profileImageURL") { doesNotExist() } }

        mockMvc.get("/api/v1/me/profile") {
            header("X-Device-ID", deviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.profileImageURL") { doesNotExist() } }
    }

    private fun createProfile(deviceID: String) {
        mockMvc.post("/api/v1/me/profile") {
            header("X-Device-ID", deviceID)
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"석범","favoriteTeamID":"samsung-lions","profileEmoji":"⚾"}"""
        }.andExpect { status { isOk() } }
    }

    private fun uploadImage(deviceID: String, filename: String): String {
        val result = mockMvc.multipart("/api/v1/me/profile/image") {
            file(jpegFile("image", filename))
            header("X-Device-ID", deviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.profileImageURL") { value(containsString("/uploads/profile/profile_")) } }
            .andReturn()

        return com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(result.response.contentAsString)
            .path("data")
            .path("profileImageURL")
            .asText()
    }

    private fun jpegFile(fieldName: String, filename: String): MockMultipartFile {
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.BLUE
        graphics.fillRect(0, 0, 64, 64)
        graphics.dispose()

        val output = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", output)
        return MockMultipartFile(fieldName, filename, MediaType.IMAGE_JPEG_VALUE, output.toByteArray())
    }
}
