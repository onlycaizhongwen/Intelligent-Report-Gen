package com.company.report.shared.security;

import com.company.report.shared.error.BusinessException;
import com.company.report.shared.error.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("权限切面单元测试")
class PermissionAspectTest {
    private final PermissionAspect aspect = new PermissionAspect();

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    @Test
    @DisplayName("REQ-AUTH-001：用户拥有权限时放行接口调用")
    void check_whenPermissionGranted_thenProceed() throws Throwable {
        CurrentUserHolder.set(new CurrentUser(1L, Set.of("admin"), Set.of("report:create")));
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.proceed()).thenReturn("ok");

        Object result = aspect.check(point, annotation("report:create"));

        assertEquals("ok", result);
        verify(point).proceed();
    }

    @Test
    @DisplayName("REQ-AUTH-001：用户缺少权限时返回 403")
    void check_whenPermissionDenied_thenThrows() {
        CurrentUserHolder.set(new CurrentUser(1L, Set.of("viewer"), Set.of("report:view")));
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.check(point, annotation("report:create")));

        assertEquals(403, ex.statusCode());
        assertEquals(ErrorCode.FORBIDDEN.code(), ex.code());
    }

    private RequiresPermission annotation(String value) {
        try {
            Method method = Fixture.class.getDeclaredMethod("secured");
            return method.getAnnotation(RequiresPermission.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class Fixture {
        @RequiresPermission("report:create")
        void secured() {
        }
    }
}
