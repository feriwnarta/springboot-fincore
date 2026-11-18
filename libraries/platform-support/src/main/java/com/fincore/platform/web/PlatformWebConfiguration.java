package com.fincore.platform.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({ApiExceptionHandler.class, RequestContextFilter.class, RequestLoggingFilter.class})
public class PlatformWebConfiguration {
}
