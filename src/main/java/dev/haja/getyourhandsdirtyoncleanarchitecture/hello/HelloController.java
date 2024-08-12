package dev.haja.getyourhandsdirtyoncleanarchitecture.hello;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicLong;

@RestController
@Slf4j
public class HelloController {
    public static final String template = "Hello, %s!";
    public static final AtomicLong counter = new AtomicLong();

    @GetMapping("hello")
    public Hello hello(@RequestParam(value = "name", defaultValue = "World") String name) {
        log.info("input_name:{}", name);
        return new Hello(counter.incrementAndGet(), String.format(template, name));
    }
}
