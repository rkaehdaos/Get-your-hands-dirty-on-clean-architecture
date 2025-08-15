# API 엔드포인트 문서

## REST API 개요

BuckPal 애플리케이션은 계좌 간 송금 기능을 제공하는 REST API를 구현하고 있습니다.

### 기본 정보
- **베이스 URL**: `http://localhost:8080`
- **Content-Type**: `application/json`
- **응답 형식**: JSON

## 엔드포인트 목록

### 1. 송금 API

#### POST /accounts/send
계좌 간 송금을 실행합니다.

**요청 구조**:
```http
POST /accounts/send HTTP/1.1
Content-Type: application/json

{
    "sourceAccountId": 1,
    "targetAccountId": 2, 
    "amount": 500
}
```

**요청 파라미터**:
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| sourceAccountId | Long | Y | 출금 계좌 ID |
| targetAccountId | Long | Y | 입금 계좌 ID |
| amount | Long | Y | 송금 금액 |

**응답**:
- **성공**: `200 OK` (응답 본문 없음)
- **실패**: `400 Bad Request` (잔액 부족, 계좌 없음 등)

**응답 예시**:
```http
HTTP/1.1 200 OK
Content-Length: 0
```

**에러 응답 예시**:
```http
HTTP/1.1 400 Bad Request
Content-Length: 0
```

### 2. 계좌 잔액 조회 (구현 예정)

#### GET /accounts/{accountId}/balance
특정 계좌의 현재 잔액을 조회합니다.

**요청 구조**:
```http
GET /accounts/1/balance HTTP/1.1
Accept: application/json
```

**경로 파라미터**:
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| accountId | Long | 조회할 계좌 ID |

**응답 구조**:
```json
{
    "accountId": 1,
    "balance": 1500
}
```

## 구현 세부사항

### SendMoneyController 분석

**위치**: `src/main/java/dev/haja/buckpal/account/adapter/in/web/SendMoneyController.java`

#### 주요 특징

1. **헥사고날 아키텍처 적용**
   - 컨트롤러는 인커밍 어댑터 역할
   - 유스케이스 포트를 통해 애플리케이션 계층과 통신
   - 웹 기술과 비즈니스 로직의 완전한 분리

2. **요청/응답 변환**
   ```java
   SendMoneyCommand command = new SendMoneyCommand(
       new AccountId(dto.sourceAccountId()),
       new AccountId(dto.targetAccountId()),
       Money.of(dto.amount())
   );
   ```
   - DTO → 도메인 Command 객체 변환
   - 타입 안전성 확보 (AccountId, Money 값 객체 활용)

3. **검증 처리**
   - `@Valid` 어노테이션을 통한 자동 검증
   - Jakarta Validation 활용

4. **응답 처리**
   - 성공/실패에 따른 적절한 HTTP 상태 코드 반환
   - RESTful 원칙 준수

### SendMoneyReqDto 구조

**위치**: `src/main/java/dev/haja/buckpal/account/adapter/in/web/SendMoneyReqDto.java`

```java
public record SendMoneyReqDto(
    @NotNull Long sourceAccountId,
    @NotNull Long targetAccountId, 
    @NotNull @Min(1) Long amount
) {}
```

**특징**:
- Java 14의 Record 타입 활용
- Jakarta Validation 어노테이션을 통한 입력 검증
- 불변 객체로 설계

## 비즈니스 규칙

### 송금 처리 흐름

1. **입력 검증**
   - 필수 필드 확인
   - 송금 금액 양수 검증

2. **비즈니스 규칙 적용**
   - 송금 한도 확인 (MoneyTransferProperties)
   - 계좌 존재 여부 확인
   - 잔액 충분성 검증

3. **거래 실행**
   - 계좌 잠금 (동시성 제어)
   - 출금 처리
   - 입금 처리
   - 상태 업데이트

4. **응답 반환**
   - 성공/실패 결과 반환

### 에러 처리

현재 구현된 에러 타입:
- **ThresholdExceededException**: 송금 한도 초과
- **IllegalStateException**: 계좌 ID 누락
- **IllegalArgumentException**: 히스토리 조회 일수 설정 오류

## 확장 가능성

### 추가 예정 API

1. **계좌 생성**: `POST /accounts`
2. **계좌 목록 조회**: `GET /accounts`
3. **거래 내역 조회**: `GET /accounts/{accountId}/activities`
4. **계좌 정보 수정**: `PUT /accounts/{accountId}`

### 비동기 처리
향후 대용량 거래 처리를 위한 비동기 API 고려:
```http
POST /accounts/send/async
Accept: application/json

Response:
{
    "transactionId": "tx-12345",
    "status": "PENDING"
}
```

### 일괄 처리
다중 계좌 송금을 위한 배치 API:
```http
POST /accounts/send/batch
Content-Type: application/json

{
    "transactions": [
        {
            "sourceAccountId": 1,
            "targetAccountId": 2,
            "amount": 100
        },
        // ...
    ]
}
```

이러한 API 설계는 헥사고날 아키텍처의 장점을 활용하여 웹 계층의 변경이 비즈니스 로직에 영향을 주지 않도록 구성되어 있습니다.