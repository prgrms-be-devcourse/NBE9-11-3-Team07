package com.back.mozu.global.exception;

import com.back.mozu.global.response.RsData;

public class ServiceException extends RuntimeException {

    private String msg;
    private String resultCode;

    public ServiceException(String resultCode, String msg) {
        super(msg);
        this.msg = msg;
        this.resultCode = resultCode;
    }

    public RsData<Void> getRsData() {
        return new RsData<>(
                msg,
                resultCode
        );
    }
}
