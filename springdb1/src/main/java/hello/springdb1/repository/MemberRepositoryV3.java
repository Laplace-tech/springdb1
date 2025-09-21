package hello.springdb1.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.NoSuchElementException;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcUtils;

import hello.springdb1.domain.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [전체 개요]
 * - 애플리케이션은 보통 3계층(프레젠테이션 / 서비스 / 데이터 접근)으로 나뉨.
 * - 서비스 계층은 핵심 비즈니스 로직만 가지도록 유지하는 것 이 목표
 * - 그러나 트랜잭션을 도입하면서 JDBC API(DataSource, Connection, SQLException)가
 *   서비스 게층으로 침투(누수)하여 유지보수성, 재사용성, 테스트 편의성이 크게 떨어짐.
 *   
 * - 스프링은 이 문제를 해결하기 위해 "트랜잭션 추상화", "트랜잭션 동기화(스레드 바인딩)",
 *   "예외 변환(checked SQLException -> 스프링의 DataAccessException 계층)",
 *   그리고 JDBC 중복을 제거하는 유틸(JdbcTemplate)을 제공
 * 
 * ---------------------------------------------------------------------
 * 1) 현재 발생하는 문제들
 * ---------------------------------------------------------------------
 * (1) 트랜잭션 문제
 *  - 트랜잭션 경계(시작/커밋/롤백)를 쓰려면 DataSource/Connection/SQLException에 "의존"해야 함
 *  - 결과 : 서비스 계층에 JDBC 코드가 섞여서 "순수한 비즈니스 로직"이 더러워짐.
 *  - 트랜잭션을 위해 Connection 을 파라미터로 넘기는 방식은 같은 트랜잭션을 유지하기 위한
 *    hack(임시방편)일 뿐이며, 코드가 번잡해지고 실수하기 쉬움.
 *  
 * (2) 트랜잭션 동기화 문제
 *  - 트랜잭션 동안 같은 커넥션을 사용하려면 "같은 스레드"에서 커넥션이 재사용되어야 함.
 *  - 직접 DataSource.getConnection()을 여러 번 호출하면 트랜잭션 매니저가 바인딩한 커넥션이 아닌
 *    새 커넥션을 얻을 위험이 있음.
 *  - 해결책은 스프링의 "트랜잭션 동기화(스레드 바인딩)"를 활용하는 것
 *  
 * (3) 예외 누수 문제
 *  - JDBC의 SQLException은 checked exception -> 서비스 레이어가 처리/선언해야 함.
 *  - SQLException은 JDBC 특화 예외라 다른 기술(JPA 등)으로 바꾸면 서비스 코드도 바뀌어야 함
 *  - 스프링은 SQLException을 런타임인 DataAccessException으로 변환해줘서 기술 교체 시
 *    서비스 계층 변경을 줄여준다.
 *   
 * (4) JDBC 반복 문제 (보일러 플레이트)
 *  - Connection, PreparedStatement, ResultSet을 열고 닫는 코드가 반복됨.
 *  - 에러 처리(try-catch-finally) 코드가 모든 메서드에 반복되어 가독성&유지보수성 저하
 *  
 * --------------------------------------------------------------------------------
 * 2) 스프링이 제공하는 핵심 해결책 (개념과 주요 컴포넌트)
 * --------------------------------------------------------------------------------
 * (1) 트랜잭션 추상화: PlatformTransactionManager
 *  - JDBC용: DataSourceTransactionManager
 *  - JPA용: JpaTransactionManager
 *  - 트랜잭션을 시작/커밋/롤백 하는 기능을 표준화한 인터페이스를 통해 구현체 교체 가능
 *  
 * (2) 선언적 트랜잭션: @Transactional (AOP 기반)
 *  - 서비스 계층의 메서드에 @Transactional을 붙이면 스프링이 트랜잭션을 열고(프록시),
 *    메서드 종료 시점에 자동으로 Commit/RollBack을 수행.
 *  - 개발자는 비즈니스 로직에만 집중하면 됨
 *  
 * (3) 트랜잭션 동기화 (Connection 바인딩)
 *  - DataSourceTransactionManager 가 트랜잭션 시작 시 Connection 을 열어
 *    TransactionSynchronizationManager 에 ConnectionHolder 로 바인딩 함.
 *  - 같은 스레드 안에서 DataSourceUtils.getConnection(dataSource) 또는 JdbcTemplate을 쓰면
 *    바인딩된 동일한 Connection 을 재사용함 -> 트랜잭션 일관성 보장
 *  - 직접 dataSource.getConnection() 을 쓰면 바인딩된 Connection 을 사용하지 못할 수 있음.
 *    (항상 DataSourceUtils 또는 JdbcTemplate을 이용하자)
 *  
 * (4) 예외 변환 (Exception Translation)
 *  - JdbcTemplate은 SQLException을 DataAccessException 계층(런타임)으로 변환.
 *  - @Repository + PersistenceExceptionTranslationPostProcessor 를 사용하면
 *    수동 DAO에서도 예외 변환이 가능.
 *  - 결과: 서비스 계층은 런타임 예외에 의존 → 기술 교체시 코드 변경 최소화.
 *  
 * (5) JDBC 보일러플레이트 제거: JdbcTemplate
 *  - 반복적인 자원 해제/예외처리/결과 매핑을 내부에서 처리
 *  - Repository 는 SQL과 매핑로직만 제공하면 됨.
 *  
 * (6) 프로그래매틱 트랜잭션: TransactionTemplate
 *  - 코드에서 직접 트랜잭션을 제어하고 싶다면 TransactionTemplate 사용.
 *  - 그러나 대부분 @Transactional(선언적)이 권장됨
 * 
 * ================================
 * ✅ 리소스 동기화 & 스프링의 해결 방식
 * ================================
 *
 * [문제 배경]
 * - 트랜잭션은 시작 ~ 종료까지 "같은 DB 커넥션"을 유지해야 함.
 * - 과거에는 같은 커넥션을 강제로 유지하기 위해 "메서드 파라미터로 Connection 객체"를 전달했음.
 *   → 단점:
 *      1) 서비스/리포지토리 코드가 지저분해짐
 *      2) Connection 을 넘기는 버전/안 넘기는 버전 메서드가 중복 발생
 *      3) 커넥션을 전달하는 로직이 누락되면 같은 트랜잭션 유지 불가
 *
 * [스프링의 해결책: 트랜잭션 동기화 매니저]
 * - 스프링은 TransactionSynchronizationManager 를 제공
 * - 내부적으로 ThreadLocal 을 사용하여 "커넥션을 현재 쓰레드에 바인딩(저장)"
 *   → 즉, 같은 쓰레드에서 실행되는 코드라면 어디서든 동일한 커넥션을 조회할 수 있음
 * - 따라서 파라미터로 Connection 을 넘길 필요가 없음
 *
 * [동작 방식]
 * 1) 트랜잭션 시작
 *    - 트랜잭션 매니저(예: DataSourceTransactionManager)가 DataSource 를 통해 커넥션 획득
 *    - 해당 커넥션을 트랜잭션 동기화 매니저(TransactionSynchronizationManager)에 보관
 *
 * 2) 리포지토리 동작
 *    - Repository 는 DataSourceUtils.getConnection(dataSource) 를 호출
 *    - DataSourceUtils 는 단순히 DataSource 에서 새로운 커넥션을 생성하지 않고,
 *      "현재 스레드에 트랜잭션 매니저가 바인딩해둔 커넥션"을 꺼내서 반환
 *    - 따라서 Repository 는 "별도 커넥션 파라미터 없이 동일한 트랜잭션 커넥션 사용 가능"
 *
 * 3) 트랜잭션 종료
 *    - 트랜잭션 매니저가 commit() 또는 rollback() 호출
 *    - 이후 트랜잭션 동기화 매니저에 보관된 커넥션도 정리(close)
 *
 * [ThreadLocal 특징]
 * - ThreadLocal 은 각 스레드마다 독립적인 저장소 제공
 * - 즉, 같은 키라도 스레드별로 별도의 값을 가짐 → 멀티스레드 환경에서도 안전하게 커넥션 공유 가능
 *
 * → 결론: 스프링의 트랜잭션 동기화 매니저 덕분에 "트랜잭션 경계만 서비스 계층에서 관리"하면 되고,
 *   리포지토리는 순수하게 SQL 실행 로직에만 집중할 수 있다.
 */


/**
 * [V2 → V3 진화 포인트]
 * - V2: Connection 객체를 서비스 계층에서 직접 제어해야 함(트랜잭션 시작) → 서비스가 JDBC 기술에 의존
 * - V3: Spring 트랜잭션 추상화를 도입 (DataSourceUtils, TransactionSynchronizationManager)
 *       → 서비스 계층은 Connection 객체에 의존하지 않음
 *       → 비즈니스 로직 + 트랜잭션 관리 코드만 다루게 됨
 */
@Slf4j
@RequiredArgsConstructor
public class MemberRepositoryV3 {

	/**
	 * 외부에서 DataSource를 주입 받아서 사용.
	 * -> HikariDataSource, DriverManagerDataSource 등을 사용
	 */
	private final DataSource dataSource;
	
	/**
	 * 테이블 생성
	 */
	public void initTable() {
		String ddl = "create table if not exists member (" + 
					 "member_id varchar(10) primary key, " + 
					 "money integer not null default 0)";

		Connection con = null;
		Statement stmt = null;
		
		try {
			con = getConnection();
			stmt = con.createStatement();
			stmt.execute(ddl);
			log.info("Table member created!");
		} catch (SQLException e) {
			log.error("DB Error - initTable: {}", ddl);
			throw new RuntimeException(e);
		} finally {
			close(stmt, con);
		}
	}
	
	/**
	 * 테이블 삭제
	 */
	public void dropTable() {
		String ddl = "drop table if exists member";

		Connection con = null;
		Statement stmt = null;

		try {
			con = getConnection();
			stmt = con.createStatement();
			stmt.execute(ddl);
			log.info("Table member dropped!");
		} catch (SQLException e) {
			log.error("DB Error - dropTable: {}", ddl);
			throw new RuntimeException(e);
		} finally {
			close(stmt, con);
		}
	}
	
	public Member save(Member member) {
		String sql = "insert into member(member_id, money) values (?, ?)";

		Connection con = null;
		PreparedStatement pstmt = null;
		
		try {
			con = getConnection();
			pstmt = con.prepareStatement(sql);
			pstmt.setString(1, member.getMemberId());
			pstmt.setInt(2, member.getMoney());
			
			int resultSize = pstmt.executeUpdate();
			log.info("save : resultSize={}", resultSize);
			return member;
		} catch (SQLException e) {
			log.error("DB Error - save: {}", sql);
			throw new RuntimeException(e);
		} finally {
			close(pstmt, con);
		}
	}
	
	public Member findById(String memberId) {
		String sql = "select * from member where member_id = ?";

		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;

		try {
			con = getConnection();
			pstmt = con.prepareStatement(sql);
			pstmt.setString(1, memberId);
			
			rs = pstmt.executeQuery();
			if (rs.next()) {
				Member findMember = new Member(
						rs.getString("member_id"), 
						rs.getInt("money"));

				log.info("findById = {}", findMember);
				return findMember;
			} else {
				throw new NoSuchElementException("member not found member_id :" + memberId);
			}
		} catch (SQLException e) {
			log.error("DB Error - findById: {}", sql);
			throw new RuntimeException(e);
		} finally {
			close(rs, pstmt, con);
		}
	}

	public void update(String memberId, int money) {
		String sql = "update member set money=? where member_id=?";

		Connection con = null;
		PreparedStatement pstmt = null;
		
		try {
			con = getConnection();
			pstmt = con.prepareStatement(sql);
			
			pstmt.setInt(1, money);
			pstmt.setString(2, memberId);
			
			int resultSize = pstmt.executeUpdate();
			log.info("update = {}", resultSize);
		} catch (SQLException e) {
			log.error("DB Error - update: {}", sql);
			throw new RuntimeException(e);
		} finally {
			close(pstmt, con);
		}
	}
	
	public void delete(String memberId) {
		String sql = "delete from member where member_id=?";
		
		Connection con = null;
		PreparedStatement pstmt = null;
		
		try {
			con = getConnection();
			pstmt = con.prepareStatement(sql);
			pstmt.setString(1, memberId);
			
			int resultSize = pstmt.executeUpdate();
			log.info("delete = {}", resultSize);
		} catch (SQLException e) {
			log.error("DB Error - delete: {}", sql);
			throw new RuntimeException(e);
		} finally {
			close(pstmt, con);
		}
	}

	public void deleteAll() {
		String sql = "delete from member";
		
		Connection con = null;
		PreparedStatement pstmt = null;
		
		try {
			con = getConnection();
			pstmt = con.prepareStatement(sql);
			
			int resultSize = pstmt.executeUpdate();
			log.info("deleteAll resultSize={}", resultSize);
		} catch (SQLException e) {
			log.error("DB Error - deleteAll: {}", sql);
			throw new RuntimeException(e);
		} finally {
			close(pstmt, con);
		}
	}
	
    private Connection getConnection() {
    	/**
    	 * DataSourceUtils.getConnection(dataSource) 사용
    	 * → 트랜잭션 동기화 매니저가 관리하는 Connection 객체가 있으면 그대로 반환 
    	 * → 없으면 새로운 커넥션 생성
    	 * 
    	 * 즉, 
    	 * - 트랜잭션 시작된 경우 → 같은 커넥션을 계속 사용 (커밋/롤백 까지 유지)
    	 * - 트랜잭션 없는 경우 → 매번 새 커넥션 생성 후, 작업 종료 시 닫음.
    	 */
    	return DataSourceUtils.getConnection(dataSource);
    }
    
    private void close(Statement stmt, Connection con) {
    	close(null, stmt, con);
    }
    
	private void close(ResultSet rs, Statement stmt, Connection con) {
		JdbcUtils.closeResultSet(rs);
		JdbcUtils.closeStatement(stmt);
		/**
		 * 커넥션은 con.close()를 사용해서 직접 닫아버리면 커넥션이 유지되지 않는 문제가 발생.
		 * 이 커넥션은 이후 로직은 물론이고, 트랜잭션을 종료(커밋, 롤백)할 때 까지 살아있어야 함.
		 * 
		 * DataSourceUtils.releaseConnection()을 사용하면 커넥션을 바로 닫지 않음.
		 * "트랜잭션을 사용하기 위해 동기화된 커넥션은 커넥션을 닫지 않고 그대로 유지"
		 * 트랜잭션 동기화 매니저가 관리하는 커넥션이 없는 경우 해당 커넥션을 닫음.
		 * 
		 * DataSourceUtils.releaseConnection(con, dataSource)
		 * → 단순히 con.close()를 호출하지 않음
		 * → 트랜잭션이 걸려있는 Connection 은 유지
		 * → 트랜잭션이 없는 경우만 실제로 닫음
		 * 
		 * 덕분에, Repository 계층에서 close()를 호출해도
		 * 트랜잭션 안정성이 깨지지 않음.
		 */
		DataSourceUtils.releaseConnection(con, dataSource);
	}
	
}