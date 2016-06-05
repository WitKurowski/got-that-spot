package com.wit.gotthatspot.exception;

public class ServerException extends Exception {
	public ServerException() {
	}

	public ServerException(final String detailMessage) {
		super(detailMessage);
	}

	public ServerException(final String detailMessage, final Throwable throwable) {
		super(detailMessage, throwable);
	}

	public ServerException(final Throwable throwable) {
		super(throwable);
	}
}