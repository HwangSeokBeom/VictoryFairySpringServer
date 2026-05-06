package com.victoryfairy.server.community

import com.fasterxml.jackson.databind.ObjectMapper
import com.victoryfairy.server.profile.UserProfileRepository
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:community-integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "victory-fairy.community.enabled=true",
        "victory-fairy.community.posts-require-profile=true",
        "victory-fairy.community.block-enabled=true",
        "victory-fairy.profile-image.upload-dir=build/test-uploads/community-integration",
    ],
)
class CommunityIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var postRepository: CommunityPostRepository
    @Autowired lateinit var reportRepository: CommunityReportRepository
    @Autowired lateinit var blockRepository: CommunityBlockRepository
    @Autowired lateinit var userProfileRepository: UserProfileRepository

    @BeforeEach
    fun clearData() {
        reportRepository.deleteAll()
        blockRepository.deleteAll()
        postRepository.deleteAll()
        userProfileRepository.deleteAll()
    }

    @Test
    fun `GET returns enabled empty state with full policy url`() {
        mockMvc.get("/api/v1/community/posts")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.data.enabled") { value(true) } }
            .andExpect { jsonPath("$.data.items", hasSize<Any>(0)) }
            .andExpect { jsonPath("$.data.message") { value("아직 응원톡이 없어요. 첫 응원을 남겨보세요.") } }
            .andExpect { jsonPath("$.data.policyURL") { value("https://hwangseokbeom.github.io/VictoryFairy-legal/community-policy.html") } }
    }

    @Test
    fun `POST requires profile before creating post`() {
        mockMvc.post("/api/v1/community/posts") {
            header("X-Device-ID", "community-no-profile-device")
            contentType = MediaType.APPLICATION_JSON
            content = """{"teamID":"samsung-lions","content":"오늘도 삼성 응원합니다!"}"""
        }
            .andExpect { status { isForbidden() } }
            .andExpect { jsonPath("$.success") { value(false) } }
            .andExpect { jsonPath("$.error.code") { value("PROFILE_REQUIRED") } }
            .andExpect { jsonPath("$.error.message") { value("응원톡을 작성하려면 프로필을 먼저 만들어 주세요.") } }
    }

    @Test
    fun `POST with profile creates visible post and GET returns it`() {
        val deviceID = "community-create-device"
        createProfile(deviceID, "석범", "samsung-lions")
        val profileImageURL = uploadProfileImage(deviceID)

        mockMvc.post("/api/v1/community/posts") {
            header("X-Device-ID", deviceID)
            contentType = MediaType.APPLICATION_JSON
            content = """{"teamID":"samsung-lions","content":"오늘도 삼성 응원합니다!"}"""
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.success") { value(true) } }
            .andExpect { jsonPath("$.data.item.teamID") { value("samsung-lions") } }
            .andExpect { jsonPath("$.data.item.teamName") { value("삼성 라이온즈") } }
            .andExpect { jsonPath("$.data.item.authorID") { value(containsString("author_")) } }
            .andExpect { jsonPath("$.data.item.authorDisplayName") { value("석범") } }
            .andExpect { jsonPath("$.data.item.authorProfileEmoji") { value("⚾") } }
            .andExpect { jsonPath("$.data.item.authorProfileImageURL") { value(profileImageURL) } }
            .andExpect { jsonPath("$.data.item.content") { value("오늘도 삼성 응원합니다!") } }
            .andExpect { jsonPath("$.data.item.status") { value("visible") } }
            .andExpect { jsonPath("$.data.policyURL") { value("https://hwangseokbeom.github.io/VictoryFairy-legal/community-policy.html") } }

        mockMvc.get("/api/v1/community/posts") {
            header("X-Device-ID", deviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.items[0].content") { value("오늘도 삼성 응원합니다!") } }
            .andExpect { jsonPath("$.data.items[0].authorDisplayName") { value("석범") } }
            .andExpect { jsonPath("$.data.items[0].authorProfileImageURL") { value(profileImageURL) } }
    }

    @Test
    fun `POST uses favorite team when teamID is omitted`() {
        val deviceID = "community-default-team-device"
        createProfile(deviceID, "라팍응원", "samsung-lions")

        mockMvc.post("/api/v1/community/posts") {
            header("X-Device-ID", deviceID)
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"응원 포인트 좋아요"}"""
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.item.teamID") { value("samsung-lions") } }
    }

    @Test
    fun `content rejected by moderation`() {
        val deviceID = "community-moderation-device"
        createProfile(deviceID, "건강응원", "samsung-lions")

        mockMvc.post("/api/v1/community/posts") {
            header("X-Device-ID", deviceID)
            contentType = MediaType.APPLICATION_JSON
            content = """{"teamID":"samsung-lions","content":"도박 홍보합니다"}"""
        }
            .andExpect { status { isBadRequest() } }
            .andExpect { jsonPath("$.error.code") { value("COMMUNITY_CONTENT_REJECTED") } }
            .andExpect { jsonPath("$.error.message") { value("건강한 응원 문화를 위해 문구를 다시 확인해 주세요.") } }
    }

    @Test
    fun `report increments and hides at threshold without duplicate device reports`() {
        val authorDeviceID = "community-report-author-device"
        createProfile(authorDeviceID, "신고테스트", "samsung-lions")
        val postID = createPost(authorDeviceID, "신고 흐름 확인용 응원")

        report(postID, "report-device-1", """{"reason":"spam"}""")
            .andExpect { jsonPath("$.data.item.reportCount") { value(1) } }
            .andExpect { jsonPath("$.data.item.status") { value("visible") } }
            .andExpect { jsonPath("$.data.message") { value("신고가 접수됐어요.") } }

        report(postID, "report-device-1")
            .andExpect { jsonPath("$.data.item.reportCount") { value(1) } }

        report(postID, "report-device-2")
            .andExpect { jsonPath("$.data.item.reportCount") { value(2) } }
            .andExpect { jsonPath("$.data.item.status") { value("visible") } }

        report(postID, "report-device-3")
            .andExpect { jsonPath("$.data.item.reportCount") { value(3) } }
            .andExpect { jsonPath("$.data.item.status") { value("hidden") } }
    }

    @Test
    fun `invalid report reason is rejected`() {
        val authorDeviceID = "community-report-reason-author-device"
        createProfile(authorDeviceID, "신고사유", "samsung-lions")
        val postID = createPost(authorDeviceID, "신고 사유 확인용 응원")

        report(postID, "report-invalid-reason-device", """{"reason":"bad-reason"}""", expectedOk = false)
            .andExpect { status { isBadRequest() } }
            .andExpect { jsonPath("$.error.code") { value("VALIDATION_ERROR") } }
    }

    @Test
    fun `block author hides posts and unblock restores them`() {
        val blockerDeviceID = "community-blocker-device"
        val authorDeviceID = "community-block-author-device"
        createProfile(blockerDeviceID, "차단자", "doosan-bears")
        createProfile(authorDeviceID, "작성자", "samsung-lions")
        val authorID = createPostItem(authorDeviceID, "차단 테스트 응원")
            .path("authorID")
            .asText()

        mockMvc.get("/api/v1/community/posts") {
            header("X-Device-ID", blockerDeviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.items", hasSize<Any>(1)) }

        mockMvc.post("/api/v1/community/users/$authorID/block") {
            header("X-Device-ID", blockerDeviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.blockedAuthorID") { value(authorID) } }
            .andExpect { jsonPath("$.data.message") { value("해당 사용자의 응원톡을 숨겼어요.") } }

        mockMvc.get("/api/v1/community/posts") {
            header("X-Device-ID", blockerDeviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.items", hasSize<Any>(0)) }

        mockMvc.get("/api/v1/community/blocked-users") {
            header("X-Device-ID", blockerDeviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.items[0].authorID") { value(authorID) } }
            .andExpect { jsonPath("$.data.items[0].authorDisplayName") { value("작성자") } }

        mockMvc.delete("/api/v1/community/users/$authorID/block") {
            header("X-Device-ID", blockerDeviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.message") { value("차단을 해제했어요.") } }

        mockMvc.get("/api/v1/community/posts") {
            header("X-Device-ID", blockerDeviceID)
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.items", hasSize<Any>(1)) }
    }

    @Test
    fun `cannot block self`() {
        val deviceID = "community-self-block-device"
        createProfile(deviceID, "본인차단", "samsung-lions")
        val authorID = createPostItem(deviceID, "내 응원")
            .path("authorID")
            .asText()

        mockMvc.post("/api/v1/community/users/$authorID/block") {
            header("X-Device-ID", deviceID)
        }
            .andExpect { status { isBadRequest() } }
            .andExpect { jsonPath("$.error.code") { value("CANNOT_BLOCK_SELF") } }
    }

    private fun createProfile(deviceID: String, nickname: String, favoriteTeamID: String) {
        mockMvc.post("/api/v1/me/profile") {
            header("X-Device-ID", deviceID)
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"$nickname","favoriteTeamID":"$favoriteTeamID","profileEmoji":"⚾"}"""
        }.andExpect { status { isOk() } }
    }

    private fun createPost(deviceID: String, contentText: String): String {
        return createPostItem(deviceID, contentText).path("id").asText()
    }

    private fun createPostItem(deviceID: String, contentText: String): com.fasterxml.jackson.databind.JsonNode {
        val result = mockMvc.post("/api/v1/community/posts") {
            header("X-Device-ID", deviceID)
            contentType = MediaType.APPLICATION_JSON
            content = """{"teamID":"samsung-lions","content":"$contentText"}"""
        }.andExpect { status { isOk() } }.andReturn()
        return objectMapper.readTree(result.response.contentAsString)
            .path("data")
            .path("item")
    }

    private fun report(
        postID: String,
        deviceID: String,
        body: String? = null,
        expectedOk: Boolean = true,
    ) =
        mockMvc.post("/api/v1/community/posts/$postID/report") {
            header("X-Device-ID", deviceID)
            if (body != null) {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        }.also {
            if (expectedOk) {
                it.andExpect { status { isOk() } }
            }
        }

    private fun uploadProfileImage(deviceID: String): String {
        val result = mockMvc.multipart("/api/v1/me/profile/image") {
            file(jpegFile("image", "community-profile.jpg"))
            header("X-Device-ID", deviceID)
        }.andExpect { status { isOk() } }.andReturn()

        return objectMapper.readTree(result.response.contentAsString)
            .path("data")
            .path("profileImageURL")
            .asText()
    }

    private fun jpegFile(fieldName: String, filename: String): MockMultipartFile {
        val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.RED
        graphics.fillRect(0, 0, 32, 32)
        graphics.dispose()

        val output = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", output)
        return MockMultipartFile(fieldName, filename, MediaType.IMAGE_JPEG_VALUE, output.toByteArray())
    }
}
