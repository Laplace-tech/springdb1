package hello.springdb1.repository;

import static hello.springdb1.connection.ConnectionConst.PASSWORD;
import static hello.springdb1.connection.ConnectionConst.URL;
import static hello.springdb1.connection.ConnectionConst.USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.NoSuchElementException;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import hello.springdb1.domain.Member;
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
 */

/**
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
 */

/**
 * ===============================================================
 * 데이터베이스 연결 구조 & 세션 이해
 * ===============================================================
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
 */

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // @BeforeAll을 non-static 으로 사용 가능
public class MemberRepositoryV1Test {

	private final DataSource hikariDataSource = createHikariDataSource();
	private final DataSource driverManagerdataSource = createDriverManagerDataSource();
	private final MemberRepositoryV1 repository = new MemberRepositoryV1(hikariDataSource);

	@BeforeAll
	void initTable() throws SQLException {
		repository.dropTable();
		repository.initTable();
	}

	@AfterEach
	void afterEach() {
		repository.dropTable();
	}

	@Test
	void crud() throws SQLException {

		// given
		Member member = new Member("memberV1", 10000);

		// when
		repository.save(member);

		// then - find
		Member findMember = repository.findById(member.getMemberId());
		assertThat(findMember).isEqualTo(member);

		// then - update
		Member updatedMember = repository.update(member.getMemberId(), 6969);
		assertThat(updatedMember.getMoney()).isEqualTo(6969);

		// then - delete
		repository.delete(member.getMemberId());
		assertThatThrownBy(() -> repository.findById(member.getMemberId()))
		.isInstanceOf(NoSuchElementException.class);
	}

	private HikariDataSource createHikariDataSource() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(URL);
		config.setUsername(USERNAME);
		config.setPassword(PASSWORD);
		config.setMaximumPoolSize(10);
		config.setPoolName("SwimmingPool");

		return new HikariDataSource(config);
	}

	private DriverManagerDataSource createDriverManagerDataSource() {
		return new DriverManagerDataSource(URL, USERNAME, PASSWORD);
	}
}
