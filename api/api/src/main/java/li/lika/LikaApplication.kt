package li.lika

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.benmanes.caffeine.cache.Caffeine
import li.lika.core.props.CorsProperties
import li.lika.core.props.LikaProperties
import li.lika.core.props.ServerProperties
import li.lika.security.props.SecurityProperties
import li.lika.webapp.SecurityConfig
import li.lika.webapp.WebConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.PropertySource
import org.springframework.scheduling.annotation.EnableScheduling
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.text.NumberFormat
import java.util.concurrent.TimeUnit


@SpringBootApplication(exclude = [ErrorMvcAutoConfiguration::class, UserDetailsServiceAutoConfiguration::class])
@EnableScheduling
@EnableCaching
@ComponentScan(basePackages = ["li.lika.core.persistence", "li.lika.core.services", "li.lika.webapp.controllers"])
@PropertySource(value = ["classpath:application-db-sqlite.properties"], ignoreResourceNotFound = true)
@PropertySource(value = ["classpath:application-db-memory.properties"], ignoreResourceNotFound = true)
@PropertySource(value = ["classpath:application-db-mongo.properties"], ignoreResourceNotFound = true)
@EnableConfigurationProperties(
    ServerProperties::class,
    CorsProperties::class,
    LikaProperties::class,
    SecurityProperties::class
)
@Import(
    value = [WebConfig::class, SecurityConfig::class]
)
class LikaApplication {

    @Bean
    fun cacheManager(caffeine: Caffeine<Any, Any>): CacheManager {
        val caffeineCacheManager = CaffeineCacheManager()
        caffeineCacheManager.setCaffeine(caffeine)
        return caffeineCacheManager
    }

    @Bean
    fun caffeineConfig(): Caffeine<Any, Any> {
        return Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES)
    }

    @Bean
    fun mapper(): ObjectMapper {
        return ObjectMapper()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .registerModule(JavaTimeModule())
            .registerKotlinModule()
    }

    companion object {

        private val logger = LoggerFactory.getLogger(LikaApplication::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(LikaApplication::class.java, *args)
            printMemInfo()
            printGCInfo()
        }

        fun printMemInfo() {
            val runtime = Runtime.getRuntime()
            val format: NumberFormat = NumberFormat.getInstance()
            val maxMemory = runtime.maxMemory()
            val allocatedMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val mb = 1024 * 1024
            val mega = " MB"

            logger.info("========================== Memory Info ==========================")
            logger.info("Free memory: " + format.format(freeMemory / mb).toString() + mega)
            logger.info("Allocated memory: " + format.format(allocatedMemory / mb).toString() + mega)
            logger.info("Max memory: " + format.format(maxMemory / mb).toString() + mega)
            logger.info(
                "Total free memory: " + format.format((freeMemory + (maxMemory - allocatedMemory)) / mb)
                    .toString() + mega
            )
            logger.info("=================================================================\n")
        }

        fun printGCInfo() {
            try {
                val gcMxBeans: List<GarbageCollectorMXBean> = ManagementFactory.getGarbageCollectorMXBeans()
                logger.info("========================== GC Info ==========================")
                for (gcMxBean in gcMxBeans) {
                    logger.info(gcMxBean.getName())
                    logger.info(gcMxBean.getObjectName().toString())
                }
                logger.info("=================================================================\n")
            } catch (re: RuntimeException) {
                throw re
            } catch (exp: Exception) {
                throw RuntimeException(exp)
            }
        }
    }

}
