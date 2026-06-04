package com.example.demo.application.domain.shared.exception;

public class DomainException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6826596750240637442L;

	public DomainException(String message) {
		super(message);
	}
}
