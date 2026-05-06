package com.victoryfairy.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class VictoryFairyApplication

fun main(args: Array<String>) {
    runApplication<VictoryFairyApplication>(*args)
}
