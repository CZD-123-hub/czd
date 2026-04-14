package com.coding.assistant.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
@WebFilter(urlPatterns = "/*")
@Order(1)
public class XssFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(new XssHttpServletRequestWrapper((HttpServletRequest) request), response);
    }

    @Override
    public void destroy() {
        // no-op
    }

    private static class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

        private static final Pattern SCRIPT_PATTERN = Pattern.compile(
                "<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile(
                "\\bon\\w+\\s*=\\s*([\"'][^\"']*[\"']|\\S+)", Pattern.CASE_INSENSITIVE);
        private static final Pattern SCRIPT_TAG_OPEN_PATTERN = Pattern.compile(
                "<script[^>]*>", Pattern.CASE_INSENSITIVE);
        private static final Pattern SCRIPT_TAG_CLOSE_PATTERN = Pattern.compile(
                "</script>", Pattern.CASE_INSENSITIVE);

        public XssHttpServletRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String[] getParameterValues(String parameter) {
            String[] values = super.getParameterValues(parameter);
            if (values == null) {
                return null;
            }

            int count = values.length;
            String[] sanitizedValues = new String[count];
            for (int i = 0; i < count; i++) {
                sanitizedValues[i] = sanitize(values[i]);
            }
            return sanitizedValues;
        }

        @Override
        public String getParameter(String parameter) {
            String value = super.getParameter(parameter);
            return value != null ? sanitize(value) : null;
        }

        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            return value != null ? sanitize(value) : null;
        }

        private String sanitize(String value) {
            if (value == null) {
                return null;
            }
            String sanitized = value;
            sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");
            sanitized = SCRIPT_TAG_OPEN_PATTERN.matcher(sanitized).replaceAll("");
            sanitized = SCRIPT_TAG_CLOSE_PATTERN.matcher(sanitized).replaceAll("");
            sanitized = EVENT_HANDLER_PATTERN.matcher(sanitized).replaceAll("");
            return sanitized;
        }
    }
}