package hello.springdb1.service;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import hello.springdb1.domain.Member;
import hello.springdb1.repository.MemberRepositoryV3;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 트랜잭션 매니저 (DataSourceTransactionManager)
 * 
 * [JDBC 기반에서 트랜잭션이 동작하는 흐름]
 * 
 * 1. 트랜잭션 시작
 *  - 서비스 계층에서 transactionManager.getTransaction() 호출 -> 트랜잭션 시작
 *  - 트랜잭션 매니저는 "내부적으로 DataSource를 통해 커넥션(Connection) 생성"
 *  - 생성된 커넥션을 "수동 커밋 모드(autoCommit=false)로 변경" → DB 트랜잭션 시작
 *  - 커넥션을 트랜잭션 동기화 매니저(TransactionSynchronizationManager)에 보관
 *  - 트랜잭션 동기화 매니저는 ThreadLocal을 사용하므로 멀티쓰레드 환경에서도 안전하게 커넥션 관리 가능
 *  
 * 2. 로직 실행
 *  - 서비스 로직 -> 리포지토리 메서드 호출
 *  - 리포지토리는 커넥션 파라미터를 직접 받지 않음
 *  - 대신 "DataSourceUtils.getConnection(dataSource)" 호출
 *     → 트랜잭션 동기화 매니저에 보관된 커넥션을 꺼내 사용
 *  - 이 과정을 통해 같은 커넥션을 공유하면서 SQL 실행
 *  - 따라서 트랜잭션이 유지됨
 *  
 * 3. 트랜잭션 종료
 *  - 비즈니스 로직이 끝나면 커밋 또는 롤백으로 트랜잭션 종료
 *  - 트랜잭션 매니저는 트랜잭션 동기화 매니저에 보관된 커넥션을 꺼냄
 *  - 해당 커넥션으로 commit() 또는 rollBack() 수행
 *  - 리소스 정리
 *    - 트랜잭션 동기화 매니저에서 커넥션 제거 (ThreadLocal 반드시 정리해야 함)
 *    - con.setAutoCommit(true) 원복 (커넥션 풀 반환 고려)
 *    - con.close() 호출 -> 커넥션 풀에 반환됨
 *    
 * [정리]
 * - 트랜잭션 추상화 덕분에 서비스 코드는 JDBC 기술에 의존하지 않음
 * - JDBC → JPA 변경 시 서비스 코드 수정 필요 없음
 *   → 단순히 의존성 주입만 DataSourceTransactionManager → JpaTransactionManager 로 교체하면 됨
 * - 트랜잭션 동기화 매니저 덕분에 "커넥션을 파라미터로 직접 전달"할 필요가 사라짐
 * - 다른 트랜잭션 매니저(JPA, Hibernate 등)도 동일한 원리로 동작하되, 각 기술에 맞게 구현만 다름
 */

/**
 * - 서비스 계층에서 @Transactional 을 선언하거나
 *   PlatformTransactionManager 로 트랜잭션을 시작하면,
 *   → 트랜잭션 동기화 매니저가 하나의 Connection 을 보관
 *   → Repository 에서 DataSourceUtils.getConnection(dataSource)을 호출하면
 *     그 보관된 Connection 을 계속 반환
 *
 *   결과:
 *   - 한 트랜잭션 내의 여러 Repository 메서드 호출이 모두 "같은 Connection"을 공유
 *   - 트랜잭션 커밋/롤백 시점까지 커넥션이 유지됨
 *   
 * - 서비스 계층이 JDBC 기술(Connection)에 의존하지 않음
 * - 스프링이 Connection 생명주기를 관리 → 안정적 트랜잭션 처리
 * - try-with-resources 대신 명시적 close() + DataSourceUtils로 제어 → 트랜잭션 안전
 */
@Slf4j
@RequiredArgsConstructor
public class MemberServiceV3_1 {

	/**
	 * [스프링 트랜잭션 추상화]
	 *  - 트랜잭션 매니저를 주입 받음. 지금은 JDBC를 사용하기 때문에 
	 *    DataSourceTransactionManager를 주입 받아야 함.
	 */
	private final PlatformTransactionManager transactionManager;
	private final MemberRepositoryV3 repository;
	
	public void accountTransfer(String fromId, String toId, int money) {
		
		// 트랜잭션 시작 
		TransactionStatus status = transactionManager
				.getTransaction(new DefaultTransactionDefinition());

		try {
			// 비즈니스 로직
			bizLogic(fromId, toId, money);
			transactionManager.commit(status); // 성공시 커밋
		} catch (Exception e) {
			transactionManager.rollback(status); // 실패시 롤백
			throw new IllegalStateException(e); 
		} 
		
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
