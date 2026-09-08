package com.mitraa.hackathon.config;

import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
public class ErrorPageConfig {

    @Bean
    public ErrorViewResolver customErrorViewResolver() {
        return (HttpServletRequest request, HttpStatus status, java.util.Map<String, Object> model) -> {
            if (status == HttpStatus.NOT_FOUND) {
                return new ModelAndView("forward:/404.html");
            }
            return null;
        };
    }
}
