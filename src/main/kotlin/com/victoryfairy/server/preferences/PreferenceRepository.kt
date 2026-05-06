package com.victoryfairy.server.preferences

import org.springframework.data.jpa.repository.JpaRepository

interface PreferenceRepository : JpaRepository<PreferenceEntity, String>
