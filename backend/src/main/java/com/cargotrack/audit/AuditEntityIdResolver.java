package com.cargotrack.audit;

import com.cargotrack.common.HasId;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class AuditEntityIdResolver {

    private AuditEntityIdResolver() {
    }

    static Long resolveId(Object result) {
        Object value = unwrap(result);
        if (value instanceof HasId hasId) {
            return hasId.id();
        }
        return invoke(value, "getId", Long.class);
    }

    static String resolveUsername(Object result) {
        Object value = unwrap(result);
        String email = invoke(value, "email", String.class);
        return email != null ? email : invoke(value, "getEmail", String.class);
    }

    private static Object unwrap(Object result) {
        return result instanceof ResponseEntity<?> response ? response.getBody() : result;
    }

    private static <T> T invoke(Object value, String methodName, Class<T> resultType) {
        if (value == null) {
            return null;
        }
        try {
            Method method = value.getClass().getMethod(methodName);
            return resultType.cast(method.invoke(value));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassCastException e) {
            return null;
        }
    }
}
