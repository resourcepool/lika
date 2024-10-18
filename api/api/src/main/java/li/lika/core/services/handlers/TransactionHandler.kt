package li.lika.core.services.handlers

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.function.Supplier


@Service
class TransactionHandler {
    @Transactional(propagation = Propagation.REQUIRED)
    fun <T> runInTransaction(supplier: Supplier<T>): T {
        return supplier.get()
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun <T> runInNewTransaction(supplier: Supplier<T>): T {
        return supplier.get()
    }
}
