package com.victoryfairy.server.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Paths

@Configuration
class WebConfig(private val properties: AppProperties) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        val allowedOriginPatterns = properties.cors.allowedOriginPatterns
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (allowedOriginPatterns.isEmpty()) return

        registry.addMapping("/**")
            .allowedOriginPatterns(*allowedOriginPatterns.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val uploadLocation = Paths.get(properties.profileImage.uploadDir)
            .toAbsolutePath()
            .normalize()
            .toUri()
            .toString()
        registry.addResourceHandler("/uploads/profile/**")
            .addResourceLocations(uploadLocation)
    }
}
