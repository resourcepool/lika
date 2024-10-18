package li.lika.core.services.handlers

import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.function.Supplier


@Service
class CacheHandler {
    @CacheEvict(cacheNames = ["permalinks", "domains", "accounts"], allEntries = true)
    fun evictAllCaches() {
    }
}
