package li.lika.core.services.handlers

import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service


@Service
class CacheHandler {
    @CacheEvict(cacheNames = ["deeplinks", "domains", "accounts"], allEntries = true)
    fun evictAllCaches() {
    }
}
