package com.victoryfairy.server.legal

import com.victoryfairy.server.common.ApiResponse
import com.victoryfairy.server.config.AppProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class LegalLinksController(private val properties: AppProperties) {
    @GetMapping("/api/v1/legal-links")
    fun links(): ApiResponse<LegalLinksData> =
        ApiResponse.ok(
            LegalLinksData(
                home = properties.legal.appHomepageUrl,
                terms = properties.legal.termsUrl,
                privacy = properties.legal.privacyPolicyUrl,
                support = properties.legal.supportUrl,
                accountDeletion = properties.legal.accountDeletionUrl,
                disclaimer = properties.legal.disclaimerUrl,
                communityPolicy = properties.legal.communityPolicyUrl,
            ),
        )
}
