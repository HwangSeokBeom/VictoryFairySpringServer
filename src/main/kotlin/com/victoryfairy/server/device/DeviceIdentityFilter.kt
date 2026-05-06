package com.victoryfairy.server.device

import com.victoryfairy.server.common.ApiException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

const val DEVICE_ID_HEADER = "X-Device-ID"

@Component
class DeviceIdentityFilter {
    fun requireDeviceID(request: HttpServletRequest): String {
        val value = request.getHeader(DEVICE_ID_HEADER)?.trim()
        if (value.isNullOrBlank() || value.length > 128) {
            throw ApiException("MISSING_DEVICE_ID", "디바이스 식별자가 필요합니다.", 401)
        }
        return value
    }
}
