package com.example.demo.iface.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.demo.application.domain.shared.exception.DomainException;
import com.example.demo.infra.shared.exception.ValidationException;

/**
 * 全域例外處理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ValidationException.class)
	public ResponseEntity<BaseExceptionResponse> handleValidationException(ValidationException e) {
		return ResponseEntity.status(HttpStatus.OK).body(new BaseExceptionResponse(e.getCode(), e.getMessage()));
	}

	@ExceptionHandler(DomainException.class)
	public ResponseEntity<BaseExceptionResponse> handleValidationException(DomainException e) {
		return ResponseEntity.status(HttpStatus.OK).body(new BaseExceptionResponse("DOMAIN_EXCEPTION", e.getMessage()));
	}

	/**
	 * 回傳訊息定義
	 */
	record BaseExceptionResponse(String code, String message) {
	}
}
