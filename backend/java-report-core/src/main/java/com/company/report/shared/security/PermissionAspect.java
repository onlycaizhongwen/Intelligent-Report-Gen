package com.company.report.shared.security;

import com.company.report.shared.error.BusinessException;
import com.company.report.shared.error.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PermissionAspect {
    /** OpenSpec: permission-collaboration / REQ-AUTH-001 / 接口级权限校验 */
    @Around("@annotation(requiresPermission)")
    public Object check(ProceedingJoinPoint point, RequiresPermission requiresPermission) throws Throwable {
        CurrentUser user = CurrentUserHolder.get();
        if (user == null || !user.enabled() || !user.hasPermission(requiresPermission.value())) {
            throw new BusinessException(403, ErrorCode.FORBIDDEN);
        }
        return point.proceed();
    }
}
