package com.back.mozu.global.exception

import com.back.mozu.global.response.RsData

sealed class ServiceException(
    val resultCode: String,
    override val message: String
) : RuntimeException(message) {

    class Common(
        resultCode: String,
        message: String
    ) : ServiceException(resultCode, message)

    fun getRsData(): RsData<Void> {
        return RsData(
            message,
            resultCode
        )
    }
}
