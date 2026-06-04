package com.example.demo.application.domain.dept.aggregate.vo;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Department Aggregate Identity (部門識別值物件)
 * <p>
 * 提供型別安全，封裝底層 ID 的字串型態。
 * </p>
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DepartmentId implements Serializable {

	
	/**
	 * 
	 */
	private static final long serialVersionUID = 8561088118300648945L;
	
	@Column(name = "id")
	private String value;

	public DepartmentId(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("DepartmentId cannot be null or blank");
		}
		this.value = value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof DepartmentId that))
			return false;
		return Objects.equals(value, that.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(value);
	}

	@Override
	public String toString() {
		return String.valueOf(value);
	}
}