package dev.haja.buckpal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.test.context.SpringBootTest;

// Hibernate가 네이티브 이미지에서 ServiceLoader로 ByteBuddy BytecodeProvider를 찾지 못해
// (BytecodeProviderImpl not found) JPA 컨텍스트 로드에 실패 → 네이티브 테스트에서 제외 (JVM에서는 정상 실행)
@SpringBootTest
@DisabledInNativeImage
class BuckpalApplicationTests {
    @Test void contextLoads() {}
}