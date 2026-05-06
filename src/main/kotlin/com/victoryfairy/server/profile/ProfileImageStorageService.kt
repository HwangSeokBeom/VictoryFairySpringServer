package com.victoryfairy.server.profile

import com.victoryfairy.server.common.ApiException
import com.victoryfairy.server.config.AppProperties
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import org.springframework.core.env.Environment

@Service
class ProfileImageStorageService(
    private val properties: AppProperties,
    private val environment: Environment,
) {
    fun store(image: MultipartFile, oldFilename: String?): StoredProfileImage {
        val config = properties.profileImage
        if (!config.uploadEnabled) {
            throw ApiException("PROFILE_IMAGE_UPLOAD_DISABLED", "프로필 이미지 업로드가 비활성화되어 있습니다.", 403)
        }
        if (image.isEmpty) {
            throw ApiException("PROFILE_IMAGE_UNSUPPORTED_TYPE", "이미지 파일을 확인해 주세요.")
        }
        if (image.size > config.maxBytes) {
            throw ApiException("PROFILE_IMAGE_TOO_LARGE", "프로필 이미지는 2MB 이하로 올려 주세요.", 413)
        }

        val sourceType = validateContentTypeAndExtension(image)
        val decoded = image.inputStream.use { ImageIO.read(it) }
            ?: throw ApiException("PROFILE_IMAGE_UNSUPPORTED_TYPE", "지원하지 않는 이미지 형식입니다. JPG 또는 PNG 파일을 사용해 주세요.")

        val resized = resize(decoded, config.maxSide)
        val preservePng = sourceType == SourceImageType.PNG && resized.colorModel.hasAlpha()
        val extension = if (preservePng) "png" else "jpg"
        val mimeType = if (preservePng) "image/png" else "image/jpeg"
        val filename = "profile_${UUID.randomUUID()}.$extension"
        val uploadDir = uploadDir()
        Files.createDirectories(uploadDir)
        val target = uploadDir.resolve(filename).normalize()
        if (!target.startsWith(uploadDir)) {
            throw ApiException("PROFILE_IMAGE_UNSUPPORTED_TYPE", "이미지 파일을 확인해 주세요.")
        }

        if (preservePng) {
            ImageIO.write(resized, "png", target.toFile())
        } else {
            writeJpeg(resized, target)
        }

        delete(oldFilename)
        return StoredProfileImage(filename = filename, mimeType = mimeType)
    }

    fun delete(filename: String?) {
        val safeFilename = safeStoredFilename(filename) ?: return
        Files.deleteIfExists(uploadDir().resolve(safeFilename).normalize())
    }

    fun toUrl(filename: String?): String? {
        val safeFilename = safeStoredFilename(filename) ?: return null
        val path = "/uploads/profile/$safeFilename"
        if (!isProductionProfile()) return path
        return "${properties.publicBaseUrl.trimEnd('/')}$path"
    }

    private fun validateContentTypeAndExtension(image: MultipartFile): SourceImageType {
        val contentType = image.contentType?.lowercase()?.substringBefore(";")?.trim()
        val extension = image.originalFilename
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.trim()

        val sourceType = when (contentType) {
            "image/jpeg" -> SourceImageType.JPEG
            "image/png" -> SourceImageType.PNG
            "image/heic", "image/heif" -> throw ApiException(
                "PROFILE_IMAGE_UNSUPPORTED_TYPE",
                "HEIC 이미지는 아직 지원하지 않습니다. JPG 또는 PNG 파일을 사용해 주세요.",
            )
            else -> throw ApiException("PROFILE_IMAGE_UNSUPPORTED_TYPE", "지원하지 않는 이미지 형식입니다. JPG 또는 PNG 파일을 사용해 주세요.")
        }

        val extensionMatches = when (sourceType) {
            SourceImageType.JPEG -> extension == "jpg" || extension == "jpeg"
            SourceImageType.PNG -> extension == "png"
        }
        if (!extensionMatches) {
            throw ApiException("PROFILE_IMAGE_UNSUPPORTED_TYPE", "지원하지 않는 이미지 형식입니다. JPG 또는 PNG 파일을 사용해 주세요.")
        }
        return sourceType
    }

    private fun resize(source: BufferedImage, maxSide: Int): BufferedImage {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxSide) {
            return source
        }

        val scale = maxSide.toDouble() / longest.toDouble()
        val targetWidth = maxOf(1, (source.width * scale).toInt())
        val targetHeight = maxOf(1, (source.height * scale).toInt())
        val targetType = if (source.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val target = BufferedImage(targetWidth, targetHeight, targetType)
        val graphics = target.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null)
        } finally {
            graphics.dispose()
        }
        return target
    }

    private fun writeJpeg(source: BufferedImage, target: Path) {
        val rgb = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        val graphics = rgb.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, rgb.width, rgb.height)
            graphics.drawImage(source, 0, 0, null)
        } finally {
            graphics.dispose()
        }

        val writers = ImageIO.getImageWritersByFormatName("jpg")
        if (!writers.hasNext()) {
            ImageIO.write(rgb, "jpg", target.toFile())
            return
        }
        val writer = writers.next()
        ImageIO.createImageOutputStream(target.toFile()).use { output ->
            writer.output = output
            val params = writer.defaultWriteParam
            if (params.canWriteCompressed()) {
                params.compressionMode = ImageWriteParam.MODE_EXPLICIT
                params.compressionQuality = 0.8f
            }
            writer.write(null, IIOImage(rgb, null, null), params)
            writer.dispose()
        }
    }

    private fun safeStoredFilename(filename: String?): String? {
        val value = filename?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (value != Paths.get(value).fileName.toString()) return null
        if (!value.startsWith("profile_")) return null
        if (!Regex("""^profile_[0-9a-fA-F-]{36}\.(jpg|png)$""").matches(value)) return null
        return value
    }

    private fun uploadDir(): Path = Paths.get(properties.profileImage.uploadDir).toAbsolutePath().normalize()

    private fun isProductionProfile(): Boolean =
        environment.activeProfiles.any { it.equals("prod", ignoreCase = true) || it.equals("production", ignoreCase = true) }

    private enum class SourceImageType {
        JPEG,
        PNG,
    }
}

data class StoredProfileImage(
    val filename: String,
    val mimeType: String,
)
