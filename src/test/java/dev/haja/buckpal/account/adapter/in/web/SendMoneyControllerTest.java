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
        
        // Mock 행동 정의 - 성공 케이스
        willReturn(true).given(sendMoneyUseCase).sendMoney(eq(new SendMoneyCommand(
                new AccountId(1L),
                new AccountId(2L),
                Money.of(500L))));

        // when & then
        mockMvc.perform(
                post("/accounts/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isOk());

        // verify
        then(sendMoneyUseCase).should()
            .sendMoney(eq(new SendMoneyCommand(
                new AccountId(1L),
                new AccountId(2L),
                Money.of(500L))));
    }
    
    @Test
    void testSendMoneyFailure() throws Exception {
        // given
        SendMoneyReqDto requestDto = new SendMoneyReqDto(1L, 2L, 500L);
        
        // Mock 행동 정의 - 실패 케이스 (false 반환)
        willReturn(false).given(sendMoneyUseCase).sendMoney(eq(new SendMoneyCommand(
                new AccountId(1L),
                new AccountId(2L),
                Money.of(500L))));

        // when & then
        mockMvc.perform(
                post("/accounts/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isBadRequest());

        // verify
        // SendMoneyUseCase.sendMoney() 메서드가 예상된 파라미터로 호출되었는지 확인
        then(sendMoneyUseCase).should()
            .sendMoney(eq(new SendMoneyCommand(
                new AccountId(1L),
                new AccountId(2L),
                Money.of(500L))));
    }
}