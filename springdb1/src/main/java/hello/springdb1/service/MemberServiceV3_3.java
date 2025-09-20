package hello.springdb1.service;

import org.springframework.transaction.annotation.Transactional;
import hello.springdb1.domain.Member;
import hello.springdb1.repository.MemberRepositoryV3;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [트랜잭션 AOP 정리]
 * 
 * 1. 지금까지의 흐름
 *  - JDBC 직접 트랜잭션 처리 (복잡, 중복 많음)
 *  - 트랜잭션 "추상화" 도입 : (PlatformTransactionManager)
 *  - 트랜잭션 "템플릿" 도입 (TransactionTemplate)
 *    -> 반복 제거했지만, 여전히 서비스 코드에 트랜잭션 로직이 섞여 있음
 *    
 * 2. AOP(프록시) 도입 전 문제
 *  - 서비스 코드 안에 여전히 "트랜잭션 시작/커밋/롤백" 코드가 존재
 *  - 순수한 비즈니스 로직만 남기지 못함
 *  
 * 3. AOP(프록시) 도입 후
 *  - 프록시가 트랜잭션 시작 -> 실제 서비스 로직 호출 -> 정상 시 커밋 / 예외 시 롤백
 *  - 서비스는 비즈니스 로직만 작성 -> 트랜잭션 로직 제거
 *  
 * 4. 스프링이 제공하는 트랜잭션 AOP
 *  - @Transactional 애너테이션 기반
 *  - 트랜잭션 프록시를 자동 생성 (CGLIB/JDK 동적 프록시)
 *  - 개발자는 @Transactional만 붙이면 됨
 *  
 * 5. 내부 동작 원리
 *    - 스프링 AOP 핵심 구성요소:
 *        ▷ Advisor : BeanFactoryTransactionAttributeSourceAdvisor
 *        ▷ Pointcut : TransactionAttributeSourcePointcut
 *        ▷ Advice  : TransactionInterceptor
 *    - 이 3가지를 스프링이 자동으로 등록해서 AOP 동작
 *    
 * 6. @Transactional 동작 원리
 *  - public 메서드에 기본 적용
 *  - 정상 종료 -> 커밋
 *  - RuntimeException/Unchecked Exception 발생 -> 롤백
 *  - Checked Exception 발생 -> 기본은 커밋
 *  
 * 7. 프록시 적용 환인 
 *  - 서비스 빈은 AOP 프록시로 감싸짐 -> CGLIB Enhancer 클래스 확인 가능
 *  - @Repository 같은 단순 빈에는 적용 안됨
 *  
 * 8. 장점 
 *  - 서비스 계층 = 순수한 비즈니스 로직만 남김
 *  - 트랜잭션 처리 로직은 완전히 분리 (관심사 분리, SRP 원칙 준수)
 *  - 코드 간결 + 유지보수 용이
 */

@Slf4j
@RequiredArgsConstructor
public class MemberServiceV3_3 {

	private final MemberRepositoryV3 repository;
	
	// 트랜잭션 AOP
	@Transactional
	public void accountTransfer(String fromId, String toId, int money) {
		bizLogic(fromId, toId, money);
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
