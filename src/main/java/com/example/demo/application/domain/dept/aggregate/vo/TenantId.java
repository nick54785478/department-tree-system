package com.example.demo.application.domain.dept.aggregate.vo;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tenant Aggregate Identity (租戶識別值物件)
 * <p>
 * 提供型別安全，避免在參數傳遞時將 TenantId 與 DepartmentId 混淆。 保證 Fail-fast，建立時即驗證不合法狀態。
 * </p>
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantId implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6863769043497215082L;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private String value;

	public TenantId(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("TenantId cannot be null or blank");
		}
		this.value = value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof TenantId that))
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