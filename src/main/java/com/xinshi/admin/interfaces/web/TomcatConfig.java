/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.coyote.ProtocolHandler
 *  org.apache.coyote.http11.AbstractHttp11Protocol
 *  org.apache.tomcat.util.net.SocketWrapperBase
 *  org.apache.tomcat.util.threads.TaskQueue
 *  org.apache.tomcat.util.threads.TaskThreadFactory
 *  org.apache.tomcat.util.threads.ThreadPoolExecutor
 *  org.apache.tomcat.util.threads.ThreadPoolExecutor$RejectedExecutionHandler
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer
 *  org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
 *  org.springframework.boot.web.server.WebServerFactoryCustomizer
 *  org.springframework.context.annotation.Bean
 *  org.springframework.context.annotation.Configuration
 */
package com.xinshi.admin.interfaces.web;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.threads.TaskQueue;
import org.apache.tomcat.util.threads.TaskThreadFactory;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {
    private static final Logger log = LoggerFactory.getLogger(TomcatConfig.class);

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers(new TomcatConnectorCustomizer[]{connector -> {
            ProtocolHandler handler = connector.getProtocolHandler();
            if (handler instanceof AbstractHttp11Protocol) {
                AbstractHttp11Protocol protocol = (AbstractHttp11Protocol)handler;
                TaskQueue taskQueue = new TaskQueue(1000);
                ThreadPoolExecutor executor = new ThreadPoolExecutor(5, 10, 60L, TimeUnit.SECONDS, (BlockingQueue)taskQueue, (ThreadFactory)new TaskThreadFactory("http-nio-exec-", true, 5));
                taskQueue.setParent(executor);
                executor.setRejectedExecutionHandler(new ThreadPoolExecutor.RejectedExecutionHandler(){

                    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                        TomcatConfig.this.handleRejectedExecution(r);
                    }
                });
                executor.prestartAllCoreThreads();
                protocol.setExecutor((Executor)executor);
                log.info("Tomcat thread pool configured: corePoolSize=5, maxPoolSize=10, queueCapacity=1000");
            }
        }});
    }

    private void handleRejectedExecution(Runnable r) {
        try {
            SocketWrapperBase<?> wrapper = this.getSocketWrapper(r);
            if (wrapper != null) {
                String body = "{\"code\":503,\"message\":\"系统繁忙，请稍后再试\"}";
                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                String httpResponse = "HTTP/1.1 503 Service Unavailable\r\nContent-Type: application/json;charset=UTF-8\r\nContent-Length: " + bodyBytes.length + "\r\nConnection: close\r\n\r\n";
                ByteBuffer headerBuf = ByteBuffer.wrap(httpResponse.getBytes(StandardCharsets.UTF_8));
                ByteBuffer bodyBuf = ByteBuffer.wrap(bodyBytes);
                wrapper.write(true, headerBuf);
                wrapper.write(true, bodyBuf);
                wrapper.close();
                return;
            }
        }
        catch (Exception ex) {
            log.warn("Failed to send busy response to rejected request", (Throwable)ex);
        }
        throw new RejectedExecutionException("系统繁忙，请稍后再试");
    }

    private SocketWrapperBase<?> getSocketWrapper(Runnable r) {
        try {
            Field field = r.getClass().getSuperclass().getDeclaredField("socketWrapper");
            field.setAccessible(true);
            return (SocketWrapperBase)field.get(r);
        }
        catch (Exception e) {
            return null;
        }
    }
}

