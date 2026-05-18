package com.back.mozu.global.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfig(
    @Value("\${spring.data.redis.host}") private val host: String,
    @Value("\${spring.data.redis.port}") private val port: Int,
) {
    // Lettuce: 비동기/논블로킹 클라이언트 → 고TPS 환경에 적합
    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory =
        LettuceConnectionFactory(host, port)

    // key/value 모두 String 직렬화 → 가독성 좋고 디버깅 편함
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

    @Bean(destroyMethod = "shutdown")
    fun redissonClient(): RedissonClient {
        val config = Config()
        config.useSingleServer().address = "redis://$host:$port"
        return Redisson.create(config)
    }
}