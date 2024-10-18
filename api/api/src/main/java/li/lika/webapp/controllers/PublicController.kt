package li.lika.webapp.controllers

import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/public")
@RestController()
class PublicController(private val buildProps: BuildProperties) {

    @GetMapping("/build")
    fun build(): BuildProperties {
        return buildProps
    }
}
