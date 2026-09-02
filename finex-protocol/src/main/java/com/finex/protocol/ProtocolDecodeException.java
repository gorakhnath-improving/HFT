package com.finex.protocol;

public class ProtocolDecodeException extends RuntimeException {

    public ProtocolDecodeException(String message) {
        super(message);
    }

    public ProtocolDecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
