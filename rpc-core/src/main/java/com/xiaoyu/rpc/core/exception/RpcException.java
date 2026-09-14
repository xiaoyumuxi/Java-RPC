package com.xiaoyu.rpc.core.exception;

import com.xiaoyu.rpc.common.vo.RpcStatusCode;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class RpcException extends RuntimeException {

    private final RpcStatusCode statusCode;
    private final String remoteErrorType;

    public RpcException(RpcStatusCode statusCode, String message) {
        this(statusCode, message, null, null);
    }

    public RpcException(RpcStatusCode statusCode, String message, Throwable cause) {
        this(statusCode, message, null, cause);
    }

    public RpcException(RpcStatusCode statusCode, String message, String remoteErrorType) {
        this(statusCode, message, remoteErrorType, null);
    }

    public RpcException(RpcStatusCode statusCode, String message, String remoteErrorType, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode == null ? RpcStatusCode.INTERNAL_ERROR : statusCode;
        this.remoteErrorType = remoteErrorType == null ? "" : remoteErrorType;
    }

    public RpcStatusCode getStatusCode() {
        return statusCode;
    }

    public String getRemoteErrorType() {
        return remoteErrorType;
    }

    public static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static RpcStatusCode statusOf(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof RpcException rpcException) {
            return rpcException.getStatusCode();
        }
        if (cause instanceof TimeoutException) {
            return RpcStatusCode.TIMEOUT;
        }
        return RpcStatusCode.INTERNAL_ERROR;
    }
}
