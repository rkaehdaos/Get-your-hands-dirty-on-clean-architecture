package dev.haja.buckpal.account.adapter.in.web;

import static dev.haja.buckpal.account.domain.Account.AccountId;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import dev.haja.buckpal.account.application.port.in.SendMoneyCommand;
import dev.haja.buckpal.account.application.port.in.SendMoneyUseCase;
import dev.haja.buckpal.account.domain.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4.0: 패키지 변경
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
// Spring Boot 4.0: Jackson 3 패키지 변경
import tools.jackson.databind.ObjectMapper;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(controllers = SendMoneyController.class)
class SendMoneyControllerTest {

    @Autowired private MockMvc mockMvc;
    
    @Autowired private ObjectMapper objectMapper;
    
    @MockitoBean private SendMoneyUseCase sendMoneyUseCase;

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
        then(sendMoneyUseCase).should()
            .sendMoney(eq(new SendMoneyCommand(
                new AccountId(1L),
                new AccountId(2L),
                Money.of(500L))));
    }
    
    @Test
    void testSendMoneyValidationFailure_NegativeAmount() throws Exception {
        // given
        @SuppressWarnings("ConstantConditions") // 의도적으로 음수 값을 사용하여 유효성 검사 실패를 테스트
        SendMoneyReqDto requestDto = new SendMoneyReqDto(1L, 2L, -500L);
        
        // when & then
        mockMvc.perform(
                post("/accounts/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.amount").exists());
        
        // verify - 유효성 검사 실패로 인해 UseCase가 호출되지 않아야 함
        verifyNoInteractions(sendMoneyUseCase);
    }
    
    @Test
    void testSendMoneyValidationFailure_NullSourceAccount() throws Exception {
        // given
        SendMoneyReqDto requestDto = new SendMoneyReqDto(null, 2L, 500L);
        
        // when & then
        mockMvc.perform(
                post("/accounts/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.sourceAccountId").exists());
        
        // verify - 유효성 검사 실패로 인해 UseCase가 호출되지 않아야 함
        verifyNoInteractions(sendMoneyUseCase);
    }
}