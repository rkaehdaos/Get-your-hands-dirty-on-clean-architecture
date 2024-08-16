package dev.haja.buckpal.common;

import jakarta.validation.*;

import java.util.Set;

public abstract class SelfValidating<T> {
    private final Validator validator;

    public SelfValidating() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            validator = validatorFactory.getValidator();
        }
    }

    /**
     * 이 인스턴스의 속성에 대한 모든 빈 유효성 검사를 평가합니다.
     */
    protected void validateSelf() {
        Set<ConstraintViolation<T>> violations = validator.validate((T) this);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
