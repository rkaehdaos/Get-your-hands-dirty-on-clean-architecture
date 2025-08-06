# Testing Standards

## Test Code Standards

### JavaDoc Requirements
- Test methods should have descriptive `@DisplayName` annotations
- JavaDoc is optional for test methods (focus on clear method names and DisplayName instead)
- Follow existing test patterns and naming conventions

### Test Structure
- Use AAA pattern (Arrange, Act, Assert)
- Use descriptive method names with BDD-style naming (`given...when...then...`)
- Group related test methods in nested classes when appropriate

### Mock Usage
- Use Mockito for mocking dependencies
- Prefer BDDMockito style (`given().willReturn()` and `then().should()`)
- Create helper methods for common mock setups

### Assertions
- Use AssertJ for fluent assertions
- Provide meaningful assertion messages when needed
- Test both success and failure scenarios

## Example Test Structure

```java
@Test
@DisplayName("계좌 이체가 성공적으로 처리되는 경우")
void givenValidAccounts_whenTransferMoney_thenTransactionSucceeds() {
    // given
    AccountId sourceAccountId = new AccountId(1L);
    Account sourceAccount = givenAnAccountWithId(sourceAccountId);
    givenWithdrawalWillSucceed(sourceAccount);
    
    // when
    boolean result = sendMoneyService.sendMoney(command);
    
    // then
    assertThat(result).isTrue();
    then(sourceAccount).should().withdraw(any(Money.class), any(AccountId.class));
}
```

## Configuration Testing
- Test both default and custom configuration values
- Verify error handling for invalid configuration
- Use factory methods for creating test configurations