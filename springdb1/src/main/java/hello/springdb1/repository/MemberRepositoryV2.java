package hello.springdb1.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.NoSuchElementException;

import javax.sql.DataSource;

import hello.springdb1.domain.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
/**
 * --------------------------------------------------- 
 * DataSource 의존성 주입(Dependency Injection) + 커넥션 풀
 * ---------------------------------------------------
 * 
 * 1) 핵심 요약 
 * - Repository 는 javax.sql.DataSource 인터페이스에만 의존하도록 설계한다. 
 * - DataSource 구현체(DriverManagerDataSource, HikariDataSource 등)는 
 *   애플리케이션 설정(또는 테스트) 시 주입한다. -> DI (Dependency Injection) 
 * - 이렇게 하면 '구현체 교체'가 가능해지고 Repository 코드는 전혀 변경할 필요가 없다. 
 *   → OCP(Open/Closed Principle): 열려있음(확장), 닫혀있음(수정 불필요)
 * 
 * 2) DriverManagerDataSource vs HikariDataSource (행동 차이)
 * - DriverManagerDataSource (스프링 제공)
 *  * 내부적으로 매번 DriverManager.getConnection(URL, USERNAME, PASSWORD)를 호출한다.
 *  * 즉, dataSource.getConnection() 할 때마다 "항상 새로운 물리 커넥션"이 생성된다.
 *  * 로그 예시 : get connection=conn1, get connection=conn2, ... (각 호출마다 다른 conn#)
 *  * 용도: 단위 테스트나 간단한 샘플
 *  
 * - HikariDataSource (HikariCP, 풀링 구현)
 *  * 초기(또는 필요 시) 물리 커넥션을 생성하여 풀에 보관한다.
 *  * dataSource.getConnection()은 "논리 커넥션(프록시)"를 반환한다.
 *    이 프록시는 내부의 물리 커넥션을 감싸며, close() 호출 시 물리 커넥션을 종료하지 않고 풀로 반납한다.
 *  * 로그 예시: 
 *   get connection=HikariProxyConnection@xxx wrapping conn0
 *   get connection=HikariProxyConnection@yyy wrapping conn0
 *   → 순차적으로 바로 반납/재대여 되는 케이스에서는 같은 물리 conn0을 재사용한다.
 *  * 용도: 실제 운영, 동시성 처리, 성능 최적화 
 * 
 * 3) 왜 테스트(단계적 호출)에선 conn1만 보이는가?
 *  - 테스트가 순차적으로 커넥션을 얻어 바로 반납하는 패턴이면 
 *    풀에 있는 물리 커넥션(예: conn0)을 계속 재사용하게 된다.
 *  - 동시에 여러 스레드가 getConnection()을 호출하면 Hikari는 pool 에서 사용 가능한 다른 물리 커넥션을 할당해준다.
 *    따라서 멀티스레드 환경에서는 다양한 물리 커넥션(conn0, conn1, ...)이 사용된다.
 *  
 * 4) 논리 vs 물리 커넥션
 *  - 물리 커넥션: DB 서버와 연결된 실제 소켓/세션 (JdbcConnection)
 *  - 논리 커넥션: 애플리케이션에 반환되는 래퍼/프록시 (HikariProxyConnection)
 *   * 애플리케이션은 논리 커넥션의 close()만 호출하면 된다. (풀에 반납)
 *   * 절대 논리 커넥션 내부의 물리 연결을 직접 close/종료하면 안됨.
 * 
 * =========================
 * 데이터베이스 연결 구조 & 세션 이해
 * =========================
 * 
 * 1) 클라이언트와 서버
 *  - 클라이언트: 웹 애플리케이션(WAS), SQL 클라이언트 도구 등
 *  - DB 서버: 요청을 처리하고 결과를 반환
 *  
 * 2) 커넥션(Connection)
 *  - 클라이언트가 DB에 연결 요청 -> DB 서버와 연결(Connection) 맺음
 *  - Connection 은 클라이언트와 DB사이의 통로 역할
 * 
 * 3) 세션(Session)
 *  - DB 서버는 Connection 하나마다 내부에 세션(Session) 생성
 *  - 세션은 연결된 커넥션을 통해 전달되는 모든 SQL 실행 단위를 관리
 *  - 세션 단위로 트랜잭션 시작, Commit/RollBack 처리
 *  - 커넥션 종료 시 세션도 종료됨
 * 
 * 4) 트랜잭션과 세션
 *  - 트랜잭션: 세션 안에서 실행되는 논리적 작업 단위
 *  - 트랜잭션 시작 -> SQL 수행 -> Commit / RollBack -> 트랜잭션 종료
 *  
 * 5) 커넥션 풀(Connection Pool)과 세션
 *  - 커넥션 풀: 미리 여러 Connection 을 생성해 풀에 보관
 *  - 예: Pool 에 10개의 Connection -> 10개의 세션 생성
 *  - 애플리케이션은 필요할 때 커넥션을 빌려 쓰고 반환
 *  - 반환 시 세션은 종료되지 않고 재사용 가능 (논리 커넥션/프록시 개념)
 * 
 * ========================
 * 트랜잭션 (Transaction) 이해
 * ========================
 * 
 * 1) 왜 DB에서 트랜잭션이 중요한가
 *  - 단순히 파일에 데이터를 쓰면 "일부만 성공/일부만 실패" 하는 문제가 생김
 *  - DB는 트랜잭션 기능을 통해 여러 작업을 "하나의 단위(거래)"로 묶어서
 *    모두 성공(Commit)하거나 모두 실패(RollBack)하도록 보장한다
 * 
 * 2) Commit & RollBack 
 *  - Commit: 트랜잭션의 모든 작업이 성공 -> DB에 영구 반영
 *  - RollBack: 트랜잭션 중 일부 실패 -> 트랜잭션 시작 이전 상태로 복구
 *  
 * 3) ACID 원칙
 *  - Atomicity (원자성) : 트랜잭션 안의 작업은 모두 성공하거나 모두 실패해야 한다.
 *  - Consistency (일관성) : 트랜잭션 후에도 DB 무결성이 유지되어야 한다.
 *  - Isolation (격리성) : 동시에 여러 트랜잭션이 실행되더라도 서로 영향을 주지 않아야 한다.
 *  - Durability (지속성) : 성공한 트랜잭션의 결과는 시스템 장애에도 보존된다.
 *  
 *     ※ 보통 DB는 원자성/일관성/지속성은 확실히 보장한다.
 *     문제는 "격리성"인데, 성능과 직결되므로 트레이드오프 필요.
 * 
 * 4) 트랜잭션 격리 수준 (ANSI/SQL 표준)
 *  - READ UNCOMMITTED : 커밋되지 않은 변경도 다른 트랜잭션에서 읽기 가능
 *   * Dirty Read 문제 발생 : 아직 commit 안 한 값을 읽음
 *   
 *  - READ COMMITTED : 커밋된 데이터만 읽음 (대부분 DB 기본값)
 *   * Dirty Read 방지
 *   * 그러나 Non-Repeatable Read 문제 발생 가능
 *    (한 트랜잭션 내에서 같은 데이터를 두 번 읽을 때 값이 달라짐)
 *    
 *  - REPEATABLE READ : 트랜잭션 동안 같은 조건의 조회는 항상 같은 결과를 보장
 *   * Dirty Read, Non-Repeatable Read 방지
 *   * 하지만 Phantom Read 발생 가능
 *    (같은 조건으로 조회 시 새로 삽입된 데이터가 보일 수 있음)
 *    
 *  - SERIALIZABLE : 가장 엄격한 수준, 사실상 트랜잭션을 순차 실행한 것과 같음
 *   * 모든 문제 방지
 *   * 성능 저하 심각 (동시성 거의 불가능)
 *   
 * [트랜잭션 사용법]
 * - 데이터 변경 후 `commit` 을 호출해야 다른 사용자에게 반영된다.
 * - `RollBack` 을 호출하면 트랜잭션 시작 전 상태로 되돌아간다.
 * - commit 전의 데이터는 해당 세션에서만 보이며, 다른 세션에는 보이지 않는다.
 * - 이유: 커밋하지 않은 변경사항이 보이면, 롤백 시 데이터 정합성이 깨지는 심각한 문제가 발생하기 때문.
 * 
 * =================
 * 자동 커밋 vs 수동 커밋
 * =================
 * [자동 커밋 모드]
 * - 기본적으로 대부분의 DB는 자동 커밋 모드(on).
 * - 쿼리를 실행할 때마다 자동으로 commit 됨.
 * - 편리하지만, 여러 쿼리를 묶어서 하나의 작업(트랜잭션)으로 처리할 수 없음.
 * - 예: 계좌이체 로직에서 중간 실패 시 → 일부만 반영되는 심각한 문제 발생.
 * 
 * [수동 커밋 모드]
 * - 명시적으로 트랜잭션을 시작하려면 autocommit을 false 로 설정.
 * - 이후 반드시 commit 또는 rollback을 직접 호출해야 한다.
 * - commit → 변경사항 확정, rollback → 변경사항 취소.
 * - 즉, `set autocommit false` = "트랜잭션 시작"이라고 표현할 수 있음.
 */


/**
 * [V1 → V2 차이점]
 * - V1: 모든 메서드 내부에서 Connection 생성/닫음 -> 항상 독립 트랜잭션 실행
 * - V2: 일부 메서드(findById, update)는 Connection 을 외부에서 주입받을 수 있음.
 *  → 서비스 계층에서 "하나의 Connection"을 여러 메서드에 전달 가능
 *  → 즉, "트랜잭션 범위를 서비스 계층이 제어"할 수 있는 구조
 *  
 * [핵심 개념 - 트랜잭션 도입]
 * - 트랜잭션은 "하나의 Connection" 단위로 동작
 * - 서비스에서 커넥션을 직접 생서앟고, Repository 메서드에 넘겨주면
 *   → 모든 SQL이 같은 Connection 을 사용
 *   → 커밋, 롤백도 서비스 계층에서 제어 가능
 *   
 *    예)
 *      try (Connection con = dataSource.getConnection()) {
 *          con.setAutoCommit(false);
 *          repo.update(con, ...);
 *          repo.findById(con, ...);
 *          con.commit();
 *      } catch (Exception e) {
 *          con.rollback();
 *      }  
 *      
 * [단점 및 개선 포인트]
 * - 서비스 계층이 Connection 객체를 집접 다루어야 함 → 순수성이 깨짐
 * - Spring TransactionManager를 사용하면 이런 의존성이 사라짐
 *   (DataSourceUtils.getConnection(dataSource) 사용 → 트랜잭션 동기화 자동 관리)
 *      
 */

@Slf4j
@RequiredArgsConstructor
public class MemberRepositoryV2 {
	
	/**
	 * 외부에서 DataSource를 주입 받아서 사용.
	 * -> HikariDataSource, DriverManagerDataSource 등을 사용
	 */
	private final DataSource dataSource;
	
	/**
	 * 테이블 생성
	 */
	public void initTable() {
		String ddl = "create table if not exists member (" 
					+ "member_id varchar(10) primary key, "
					+ "money integer not null default 0)";

		try (Connection con = getConnection(dataSource);
			 Statement stmt = con.createStatement()) {

			stmt.execute(ddl);
			log.info("Table member created!");
		} catch (SQLException ex) {
			log.error("DB Error : {}", ex.getMessage());
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * 테이블 삭제
	 */
	public void dropTable() {
		String ddl = "drop table if exists member";

		try (Connection con = getConnection(dataSource);
			 Statement stmt = con.createStatement()) {

			stmt.execute(ddl);
			log.info("Table member dropped!");
		} catch (SQLException ex) {
			log.error("DB Error : {}", ex.getMessage());
			throw new RuntimeException(ex);
		}
	}
	
	public Member save(Member member) {
		String sql = "insert into member(member_id, money) values (?, ?)";

		try (Connection con = getConnection(dataSource);
			 PreparedStatement pstmt = con.prepareStatement(sql)) {

			pstmt.setString(1, member.getMemberId());
			pstmt.setInt(2, member.getMoney());

			int resultSize = pstmt.executeUpdate();
			log.info("save : resultSize={}", resultSize);
			return member;
		} catch (SQLException ex) {
			log.error("DB Error : {}", ex.getMessage());
			throw new RuntimeException(ex);
		}
	}
	
	public Member findById(Connection con, String memberId) {
		String sql = "select * from member where member_id = ?";

		try (PreparedStatement pstmt = con.prepareStatement(sql)) {
			pstmt.setString(1, memberId);

			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					Member findMember = new Member(
							rs.getString("member_id"), 
							rs.getInt("money"));
					
					log.info("findById = {}", findMember);
					return findMember;
				} else {
					throw new NoSuchElementException("member not found member_id :" + memberId);
				}
			}
		} catch (SQLException ex) {
			log.error("DB Error : {}", ex.getMessage());
			throw new RuntimeException(ex);
		}
	}
	
	public Member findById(String memberId) {
		try (Connection con = getConnection(dataSource)) {
			return findById(con, memberId);
		} catch (SQLException ex) {
			log.error("DB Error : {}", ex.getMessage());
			throw new RuntimeException(ex);
		}
	}

	public void update(Connection con, String memberId, int money) {
		String sql = "update member set money=? where member_id=?";

		try (PreparedStatement pstmt = con.prepareStatement(sql)) {

			pstmt.setInt(1, money);
			pstmt.setString(2, memberId);
			
			int resultSize = pstmt.executeUpdate();
			log.info("update = {}", resultSize);
		} catch (SQLException ex) {
			log.error("DB Error : {}", ex.getMessage());
			throw new RuntimeException(ex);
		}
	}
	
	public void update(String memberId, int money) {
		try (Connection con = getConnection(dataSource)) {
			update(con, memberId, money);
		} catch (SQLException ex) {
			log.error("DB Error : {}", ex.getMessage());
			throw new RuntimeException(ex);
		}
	}
	
	public void delete(String memberId) {
		String sql = "delete from member where member_id=?";
		
		try (Connection con = getConnection(dataSource);
			 PreparedStatement pstmt = con.prepareStatement(sql)) {

			pstmt.setString(1, memberId);
			
			int resultSize = pstmt.executeUpdate();
			log.info("delete = {}", resultSize);
		} catch (SQLException ex) {
			log.error("DB Error : {}", ex.getMessage());
			throw new RuntimeException(ex);
		}
	}

	public void deleteAll() {
		String sql = "delete from member";
		
		try (Connection con = getConnection(dataSource);
			 PreparedStatement pstmt = con.prepareStatement(sql)) {
			
			int resultSize = pstmt.executeUpdate();
			log.info("deleteAll resultSize={}", resultSize);
		} catch (SQLException e) {
			log.error("DB Error", e);
			throw new RuntimeException(e);
		}
	}
	
	private Connection getConnection(DataSource dataSource) throws SQLException {
		Connection con = dataSource.getConnection();
		log.info("Get Connection = {}, class = {}", con, con.getClass());
		return con;
	}

}
