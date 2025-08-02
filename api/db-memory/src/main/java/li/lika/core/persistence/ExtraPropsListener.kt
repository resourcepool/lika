package li.lika.core.persistence

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.env.PropertiesPropertySource
import java.util.*

class ExtraPropsListener : ApplicationListener<ApplicationEnvironmentPreparedEvent?> {
    override fun onApplicationEvent(event: ApplicationEnvironmentPreparedEvent) {
        // NO-OP
    }
}
