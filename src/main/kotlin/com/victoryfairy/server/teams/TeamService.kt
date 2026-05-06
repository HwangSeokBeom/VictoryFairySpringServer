package com.victoryfairy.server.teams

import org.springframework.stereotype.Service

@Service
class TeamService {
    fun listTeams(): List<TeamDto> = TeamSeed.teams
}
