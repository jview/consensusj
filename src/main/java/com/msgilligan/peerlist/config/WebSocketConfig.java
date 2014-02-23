package com.msgilligan.peerlist.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * User: sean
 * Date: 2/22/14
 * Time: 11:30 PM
 */
@Configuration
@EnableWebSocketMessageBroker
@ComponentScan(basePackages="com.msgilligan.peerlist")
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/stomp").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(4).maxPoolSize(10);
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        return true;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry configurer) {
        configurer.enableSimpleBroker("/queue/", "/topic/");
        configurer.setApplicationDestinationPrefixes("/app");
    }

}