package com.example.recruitmentsystem.config;

import com.example.recruitmentsystem.service.RecruitmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    @Autowired
    private RecruitmentService recruitmentService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Skip authentication for OPTIONS requests (CORS preflight)
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        String username = request.getHeader("X-Username");
        if (username == null || username.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Authentication required\"}");
            response.setContentType("application/json");
            return false;
        }

        if (!recruitmentService.isSessionValid(username)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Session expired\"}");
            response.setContentType("application/json");
            return false;
        }

        return true;
    }
}