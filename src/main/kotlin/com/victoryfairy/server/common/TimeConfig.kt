package com.victoryfairy.server.common

import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TimeConfig {
    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()
}
