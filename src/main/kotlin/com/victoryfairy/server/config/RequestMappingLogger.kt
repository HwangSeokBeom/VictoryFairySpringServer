package com.victoryfairy.server.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

@Component
class RequestMappingLogger(
    private val handlerMapping: RequestMappingHandlerMapping,
    private val environment: Environment,
) : ApplicationListener<ApplicationReadyEvent> {
    private val logger = LoggerFactory.getLogger(RequestMappingLogger::class.java)

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        if (!shouldLog()) return

        val mappings = handlerMapping.handlerMethods.keys.flatMap { info ->
            val methods = info.methodsCondition.methods.map { it.name }.ifEmpty { listOf("ANY") }
            val patterns = info.pathPatternsCondition?.patternValues ?: info.patternsCondition?.patterns ?: emptySet()
            patterns.flatMap { pattern -> methods.map { method -> "$method $pattern" } }
        }.sorted()

        logger.info("Registered request mappings ({}): {}", mappings.size, mappings.joinToString(", "))
    }

    private fun shouldLog(): Boolean {
        val profiles = environment.activeProfiles.map { it.lowercase() }
        if (profiles.any { it == "prod" || it == "production" }) return false
        return profiles.isEmpty() || profiles.any { it == "local" || it == "dev" || it == "test" }
    }
}
