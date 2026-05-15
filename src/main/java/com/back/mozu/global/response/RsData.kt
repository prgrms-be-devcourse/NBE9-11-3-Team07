package com.back.mozu.global.response

import com.fasterxml.jackson.annotation.JsonIgnore

data class RsData<T> @JvmOverloads constructor(
    val msg: String,
    val resultCode: String,
    val data: T? = null,
) {
    companion object {
        @JvmStatic
        fun <T> of(
            resultCode: String,
            msg: String,
            data: T,
        ): RsData<T> = RsData(msg = msg, resultCode = resultCode, data = data)
    }

    @JsonIgnore
    fun getStatusCode(): Int = resultCode.split("-")[0].toInt()
}