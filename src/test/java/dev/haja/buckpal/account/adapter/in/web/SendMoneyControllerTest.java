package dev.haja.buckpal.account.adapter.in.web;

import static dev.haja.buckpal.account.domain.Account.AccountId;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.haja.buckpal.account.application.port.in.SendMoneyCommand;
import dev.haja.buckpal.account.application.port.in.SendMoneyUseCase;
import dev.haja.buckpal.account.domain.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.BDDMockito.willReturn;

@WebMvcTest(controllers = SendMoneyController.class)
class SendMoneyControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private SendMoneyUseCase sendMoneyUseCase;

    @Test
    void testSendMoney() throws Exception {
        // given
        SendMoneyReqDto requestDto = new SendMoneyReqDto(1L, 2L, 500L);
        
        // Mock 행동 정의
        // `willDoNothing`은 void 메서드에서만 사용 가능
        // SendMoneyUseCase 인터페이스의 sendMoney는 boolean 타입을 반환
        // boolean 변환 타입에 맞게 willReturn(True)로 변경
        willReturn(true).given(sendMoneyUseCase).sendMoney(eq(new SendMoneyCommand(
                new AccountId(1L),
                new AccountId(2L),
                Money.of(500L))));

        // when & then
        mockMvc.perform(
                post("/accounts/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestDto))) //json 형식으로 설정
            .andExpect(status().isOk());

        // verify
        then(sendMoneyUseCase).should()
            .sendMoney(eq(new SendMoneyCommand(
                new AccountId(1L),
                new AccountId(2L),
                Money.of(500L))));
    }
}