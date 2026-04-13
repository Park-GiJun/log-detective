package com.gijun.logdetect.detection

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DetectionServiceApplication

fun main(args: Array<String>) {
    runApplication<DetectionServiceApplication>(*args)
}
