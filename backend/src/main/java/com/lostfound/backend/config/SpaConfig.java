package com.lostfound.backend.config;

import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built React app when it's packaged inside the jar (the single-container deployment, see the
 * root Dockerfile). In local development there is no static/index.html, and nothing here does anything.
 *
 * <ul>
 *   <li>/assets/* (file names contain a content hash) are cached by browsers for a year.</li>
 *   <li>Any other path that isn't a real file and isn't /api or /actuator returns index.html, so React
 *       Router can show pages like /items/5 even when opened directly or refreshed.</li>
 * </ul>
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource file = location.createRelative(path);
                        // "" is the folder itself (the home page "/"), not a file to send.
                        if (!path.isEmpty() && file.exists() && file.isReadable()) return file;
                        // Unknown API URLs must stay 404s, not turn into the home page.
                        if (path.startsWith("api/") || path.startsWith("actuator/")) return null;
                        Resource index = location.createRelative("index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
