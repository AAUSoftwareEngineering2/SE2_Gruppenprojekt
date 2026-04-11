package at.aau.kuhhandel.server.api

import at.aau.kuhhandel.shared.ApiRoutes
import at.aau.kuhhandel.shared.HealthResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    @GetMapping(ApiRoutes.HEALTH)
    fun getHealth(): HealthResponse = HealthResponse(status = "UP")
}
