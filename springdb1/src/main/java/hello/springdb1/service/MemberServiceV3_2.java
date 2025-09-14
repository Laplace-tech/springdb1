package hello.springdb1.service;

import java.sql.SQLException;

import org.springframework.transaction.support.TransactionTemplate;

import hello.springdb1.domain.Member;
import hello.springdb1.repository.MemberRepositoryV3;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * [트랜잭션 템플릿 개념 정리]
 * 
 * 1. 트랜잭션을 사용하는 로직의 반복 패턴 
 * - 트랜잭션 시작 
 * - 비즈니스 로직 실행 
 * - 성공 시 커밋 
 * - 실패(예외) 시 롤백 
 * - try, catch, finally 코드가 모든 서비스 로직에 반복됨.
 * 
 * 2. 문제점 
 * - 서비스 로직(핵심 비즈니스 로직) + 트랜잭션 관리(부가 기능)가 섞여서 코드가 복잡해짐 
 * - 중복되는 코드(트랜잭션 시작/커밋/롤백)가 모든 서비스마다 계속 반복죔
 * 
 * 3. 해결법 
 * - "반복되는 패턴"은 템플릿으로 묶고, "달라지는 부분(비즈니스 로직)"만 콜백(람다메서드)으로 전달 
 * - 스프링은 TransactionTemplate 클래스로 이 패턴을 재공
 * 
 * 4. TransactionTemplate 주요 메서드 
 * - <T> T execute(TransactionCallback<T> action) 
 * 		→ 결과값이 필요한 경우 
 * - void executeWithoutResult(Consumer<TransactionStatus> action)
 * 	    → 결과값이 필요 없는 경우
 * 
 * 5. 트랜잭션 템플릿 기본 동작
 * - 비즈니스 로직 정상 수행 -> 커밋
 * - 언체크 예외(RuntimeException) 발생 -> 롤백
 * - 체크 예외(Exception, SQLException)등 발생 -> 기본은 커밋
 *  -> 따라서 체크 예외를 롤백시키려면 언체크 예외로 변환 필요
 *  
 * 6. 장점 
 * - 트랜잭션 시작, 커밋, 롤백 같은 반복 코드 제거
 * - 서비스 코드에서 비즈니스 로직만 남아 가독성 향상
 * 
 * 7. 한계
 * - 여전히 서비스 로직 안에 "트랜잭션 관리 코드"라는 기술적인 요소가 들어옴
 * - 즉, 핵심 관심사(비즈니스 로직) + 부가 관심사(트랜잭션 처리)가 섞여 있음.
 * - 유지보수성과 확장성에 한계 -> 이후 AOP(@Transactional)로 발전
 */

@Slf4j
@RequiredArgsConstructor
public class MemberServiceV3_2 {
	
	private final TransactionTemplate txTemplate;
	private final MemberRepositoryV3 repository;

	public void accountTransfer(String fromId, String toId, int money) throws SQLException {
		txTemplate.executeWithoutResult(status -> bizLogic(fromId, toId, money));
	}

	
	private void bizLogic(String fromId, String toId, int money) {
		Member fromMember = repository.findById(fromId);
		Member toMember = repository.findById(toId);
		
		repository.update(fromId, fromMember.getMoney() - money);
        validation(toMember);
        repository.update(toId, toMember.getMoney() + money);

        log.info("계좌이체 완료: {} → {} 금액={}", fromId, toId, money);
	}
	
    private void validation(Member toMember) {
        if ("ex".equals(toMember.getMemberId())) {
            log.warn("이체 대상 검증 실패: memberId={}", toMember.getMemberId());
            throw new IllegalStateException("이체 중 예외 발생");
        }
    }
}
