package com.example.demo.application.domain.dept.aggregate.vo;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Department Business Code (部門業務代碼值物件)
 * <p>
 * 封裝代碼的長度與空白驗證規則，保證資料進入 Aggregate 時即是合法的。
 * </p>
 */
@Embeddable
public class DepartmentCode {

	@Column(name = "code")
	private String value;

	protected DepartmentCode() {
	}

	public DepartmentCode(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("DepartmentCode cannot be blank");
		}
		if (value.length() > 50) {
			throw new IllegalArgumentException("DepartmentCode too long");
		}
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof DepartmentCode that))
			return false;
		return Objects.equals(value, that.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(value);
	}

	@Override
	public String toString() {
		return value;
	}
}