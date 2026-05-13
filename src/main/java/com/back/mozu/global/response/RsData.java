package com.back.mozu.global.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record RsData<T>(
        String msg,
        String resultCode,
        String errorCode,
        T data
) {
    public static <T> RsData<T> of(String resultCode, String msg, T data) {
        return new RsData<>(msg, resultCode, data);
    }

    // 3개짜리 생성자 (내부 record 생성 및 데이터 포함 시 사용)
    public RsData(String msg, String resultCode, T data) {
        this(msg, resultCode, null, data);
    }

    // 2개짜리 생성자 (데이터 없이 성공/실패 메시지만 보낼 때)
    public RsData(String msg, String resultCode) {
        this(msg, resultCode, null, null);
    }

    // HTTP 상태 코드 추출기 (ResponseEntity와 연동)
    @JsonIgnore
    public int getStatusCode() {
        return Integer.parseInt(resultCode.split("-")[0]);
    }
}