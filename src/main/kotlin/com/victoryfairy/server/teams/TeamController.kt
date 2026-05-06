package com.victoryfairy.server.teams

import com.victoryfairy.server.common.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/teams")
class TeamController(private val teamService: TeamService) {
    @GetMapping
    fun teams(): ApiResponse<List<TeamDto>> = ApiResponse.ok(teamService.listTeams())
}
