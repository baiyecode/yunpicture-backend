package com.yupi.yupicturebackend.manager.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

/**
 * WebSocket 配置类(定义连接）
 */
@Configuration
@EnableWebSocket //启用 WebSocket 支持
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private PictureEditHandler pictureEditHandler;

    @Resource
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    /**
     * 注册 WebSocket 处理器
     * @param registry
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        //WebSocketHandlerRegistry 是 Spring 提供的用于注册 WebSocket 端点的注册表。

        // websocket
        //registry.addHandler(...): 向注册表中添加一个 WebSocket 处理器。
        //pictureEditHandler: 指定使用哪个处理器 Bean 来处理消息，这里就是注入的 PictureEditHandler。
        //"/ws/picture/edit": 指定 WebSocket 连接的端点路径（Endpoint Path）。
        //客户端需要连接到这个 URL 才能建立 WebSocket 连接。
        registry.addHandler(pictureEditHandler, "/ws/picture/edit")
                .addInterceptors(wsHandshakeInterceptor) // 添加拦截器,校验权限
                .setAllowedOrigins("*");//允许跨域
    }
}
