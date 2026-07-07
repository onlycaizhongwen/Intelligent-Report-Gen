package com.company.report.shared.security;

import com.company.report.shared.error.BusinessException;
import com.company.report.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PermissionInterceptor implements HandlerInterceptor {
    /** OpenSpec: permission-collaboration / REQ-AUTH-001 / check endpoint permission before body binding. */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequiresPermission requiresPermission = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (requiresPermission == null) {
            return true;
        }

        CurrentUser user = CurrentUserHolder.get();
        if (user == null || !user.enabled() || !user.hasPermission(requiresPermission.value())) {
            throw new BusinessException(403, ErrorCode.FORBIDDEN);
        }
        return true;
    }
}
