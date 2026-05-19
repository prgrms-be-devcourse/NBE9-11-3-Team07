package com.back.mozu.global.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisSentinelConfiguration // [변경] 단일 노드 → Sentinel 설정 import 추가
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfig(
    // [변경] host, port → sentinel master, nodes로 교체
    @Value("\${spring.data.redis.sentinel.master}") private val master: String,
    @Value("\${spring.data.redis.sentinel.nodes}") private val nodes: String,
) {
    // [변경] 단일 노드 LettuceConnectionFactory → Sentinel 구성으로 변경
    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val sentinelConfig = RedisSentinelConfiguration()
        sentinelConfig.master(master)
        // nodes = "localhost:26379,localhost:26380,localhost:26381" 형태로 파싱
        nodes.split(",").forEach { node ->
            val (host, port) = node.trim().split(":")
            sentinelConfig.sentinel(host, port.toInt())
        }
        return LettuceConnectionFactory(sentinelConfig)
    }

    // [변경 없음] redisTemplate은 그대로 유지
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
        val template = RedisTemplate<String, String>()
        template.setConnectionFactory(connectionFactory)
        val serializer = StringRedisSerializer()
        template.setKeySerializer(serializer)
        template.setValueSerializer(serializer)
        template.setHashKeySerializer(serializer)
        template.setHashValueSerializer(serializer)
        template.afterPropertiesSet()
        return template
    }

    // [변경] useSingleServer → useSentinelServers로 교체
    @Bean(destroyMethod = "shutdown")
    fun redissonClient(): RedissonClient {
        val config = Config()
        config.useSentinelServers()
            .setMasterName(master)
            .addSentinelAddress(
                *nodes.split(",").map { "redis://${it.trim()}" }.toTypedArray()
            )
        return Redisson.create(config)
    }
}