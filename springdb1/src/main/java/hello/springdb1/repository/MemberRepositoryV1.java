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
 * ---------------------------- 
 * [DB 커넥션 풀 & DataSource 정리]
 * ----------------------------
 * 
 * 1) 왜 커넥션 풀이 필요한가? 
 * - 매 요청마다 "물리 커넥션"을 새로 만드는 과정은 비싼 연산이다. 
 *   (드라이버 로딩 → TCP 3-way handshake → 인증/세션 생성 → 커넥션 객체 반환) 
 * - SQL 실행 시간 + 커넥션 생성 시간이 합쳐저 "응답 지연" 발생. 
 * - 커넥션 풀은 애플리케이션 시작 시 커넥션(물리 커넥션)을 여러 개 미리 만들어서 풀에 보관해두고, 
 *   요청 시 즉시 "대여"해 쓰기 때문에 지연을 크게 줄여줌. 
 * - 추가 이점: 서버별 최대 커넥션 수를 제한 → DB 과부화/폭주 방지(보호막 역할)
 * 
 * 2) DataSource vs DriverManager
 * - DriverManager.getConnection(...)은 호출할 때마다 "새 커넥션"을 만듦.
 * - DataSource 는 커넥션을 "공급"하는 표준 인터페이스(팩토리) 
 *   * 일반 구현: 매번 새 커넥션 생성(테스트/간단 용도) : DriverManagerDataSource
 *   * 풀링 구현: 커넥션 풀에서 가져와 반환(실무 표준) : HikariDataSource 
 * - 장점: 설정 주입/외부화 용이, 테스트 용이, 서버/프레임워크가 리소스를 관리하기 쉬움 
 * - 스프링은 DataSource를 빈으로 주입받아 JDBCTemplate/JPA 등이 재사용.
 * 
 * 3) 커넥션 풀의 핵심 동작
 * - 애플리케이션 부팅 시: 풀 초기화 → 미리 N개의 물리 커넥션 생성/보관(기본 10 내외가 일반적임) 
 * - 런 타임: 
 *  1) 스레드가 DataSource.getConnection() 호출 → "논리 커넥션(프록시)" 대여 
 *  2) SQL 수행(물리 커넥션과 이미 TCP로 연결됨 → 즉시 전송) 
 *  3) con.close() 호출 시 진짜 종료가 아니라 커넥션 풀로 "반납" 
 * - 반납 시 풀 구현은 "커넥션 상태를 초기화"(autocommit/readOnly/isolation 등)하여 다음 사용자에게 안전하게 재사용.
 *
 * 4) 주요 용어 정리 
 * - 물리 커넥션(Physical): DB까지 실제로 연결된 소켓/세션 
 * - 논리 커넥션(Logical) : 애플리케이션에 반환되는 래퍼/프록시. close() 하면 반납. 
 * - 풀 사이즈: 동시에 빌려줄 수 있는 최대 커넥션 개수(= 동시 DB 세션 상한) 
 * - 고갈(Exhaustion): 동시에 빌린 수가 최대치에 도달해 대기/타임아웃이 발생하는 상태.
 * 
 * 5) HikariCP(현업 표준) 핵심 파라미터 
 * - maximumPoolSize : 풀 최대 크기(동시성 상한). 너무 크면 DB가 병목/메모리 압박 
 * - minimumIdle : 한가할 때 유지할 유휴 커넥션 개수(즉시 응답성) 
 * - connectionTimeout : 풀에서 커넥션을 빌릴 때 대기 타임아웃(ms) 초과 시 예외. 
 * - idleTimeout : 유휴(사용 안 함) 커넥션을 풀에서 정리하는 시간. 
 * - maxLifetime : 커넥션의 최대 수명. 너무 길면 오래된 커넥션 누수/네트워크 이슈 누적 
 * 				   너무 짧으면 재생성 비용↑. 보통 DB/네트워크 정책보다 약간 짧게. 
 * - leakDetectionThreshold : 커넥션을 빌리고 오래 반납하지 않으면 로그로 경고(누수 탐지)
 * 
 * 6) 풀 사이즈 튜닝: 직감이 아니라 "측정 + 공식"으로 
 * - 리틀의 법칙: 필요 커넥션 수 ≈ 요청도착률(초당 Tx) x 평균 DB점유시간(초) 
 *   예) 초당 200건, 쿼리/트랜잭션 평균 DB 점유 0.05초 -> 200× 0.05 = 10개 필요 
 * - 실무 팁:
 *   * 1차 추정치로 시작(예: 10~30), 부하 테스트로 조정
 *   * 애플리케이션 인스턴스 수 × maximumPoolSize ≤ DB의 허용 커넥션
 *   * 느린 쿼리/락 경합 줄이기가 풀 키우기보다 효과적일 때가 많음(인덱스/쿼리 튜닝 먼저).
 * 
 * 7) 트랜잭션 & 상태 관리 주의 
 * - 커넥션은 스레드마다 빌려 쓰고 "반드시" finally/try-with-resources로 반납 
 * - 오토커밋 사용 여부: 
 *   * 스프링 @Transactional 사용 시 수동으로 setAutoCommit(false) 금지(프레임워크가 관리). 
 * - 트랜잭션 경계(시작~커밋/롤백)를 짧게. "커넥션 장시간 점유 = 풀 고갈의 지름길" 
 * - 커넥션 상태(격리수준/읽기전용/스키마 등)를 변경했다면, 프레임워크나 풀에서 초기화해줌. 
 *   직접 관리 시 초기화 누락 주의.
 * 
 * 8) 검사/헬스체크 
 * - 풀은 커넥션 유효성 검사를 수행(드라이버 isValid() 또는 connectionTestQuery). 
 * - 장애/네트워크 사일런트 드랍 대비: maxLifetime, keepalive(드라이버/DB 정책)로 늙은 커넥션 교체. 
 * - 모니터링: active(대여 중), idle(유휴), pending(대기) 지표를 항상 본다. 
 *   connectionTimeout 예외가 보이면 풀 고갈/느린 쿼리/미반납을 의심.
 * 
 * 9) 자주 겪는 문제와 처방 
 * - (1) 간헐적 "커넥션 불가/타임아웃" 
 * 		→ maximumPoolSize 부족, 느린/락 경합 쿼리, 트랜잭션 범위 과대, 커넥션 누수 점검. 
 * - (2) 'close 안 해서' 누수 
 * 		→ try-with-resources 사용. HikarileakDetectionThreshold로 탐지 로그 켜기. 
 * - (3) DB가 커넥션을 중간에 끊음 
 *  	→ maxLifetime을 DB의 세션 만료/네트워크 idle보다 짧게 설정, 유효성 검사 강화. 
 * - (4) 인스턴스 확장 시 DB가 버티지 못함 
 * 		→ 인스턴스 수 × 풀 크기가 DB max_connections를 넘지 않도록 합리화. 
 * - (5) 혼합 사용 금지 
 * 		→ 스프링 트랜잭션(@Transactional)과 직접 커밋/롤백 혼용 금지(예상 못한 상태 꼬임).
 * 
 * 10) 어떤 풀을 쓸까? 
 * - 현업 기본: HikariCP (성능/안전성/편의성 우수, 스프링 부트 기본). 
 * - 레거시: commons-dbcp2, tomcat-jdbc 등도 존재하나 신규는 보통 Hikari 권장.
 * 
 * 11) 정리 키워드 
 * - "반드시 반납", "짧은 트랜잭션", "풀 사이즈는 측정으로", "DB 보호를 위한 상한", 
 * 	 "모니터링 기반 튜닝", "스프링이면 @Transactional에 위임"
 */

/**
 * [개선 포인트 V0 → V1]
 * 
 * DataSource 외부 주입
 *  - DB 연결을 얻는 책임을 외부로 위임
 *  - HikariCP(운영) / DriverManagerDataSource(테스트) 등 유연하게 교체 가능
 *  - Spring 환경에서 DI 컨테이너가 관리하는 DataSource를 주입받으면 트랜잭션 매니저와도 쉽게 연동 가능
 */

@Slf4j
@RequiredArgsConstructor
public class MemberRepositoryV1 {

	/**
	 * 외부에서 DataSource를 주입 받아서 사용.
	 * -> HikariDataSource, DriverManagerDataSource 등을 사용
	 */
	private final DataSource dataSource;
	
    // ========== 테이블 관리 ==========   
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
	
	// ========== CRUD ==========
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

	public Member findById(String memberId) {
		String sql = "select * from member where member_id = ?";

		try (Connection con = getConnection(dataSource);
			 PreparedStatement pstmt = con.prepareStatement(sql)) {

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

	public void update(String memberId, int money) {
		String sql = "update member set money=? where member_id=?";

		try (Connection con = getConnection(dataSource);
			 PreparedStatement pstmt = con.prepareStatement(sql)) {

			pstmt.setInt(1, money);
			pstmt.setString(2, memberId);
			
			int resultSize = pstmt.executeUpdate();
			log.info("update = {}", resultSize);
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
	
//	private void close(Connection con, Statement stmt, ResultSet rs) {
//		JdbcUtils.closeResultSet(rs);
//		JdbcUtils.closeStatement(stmt);
//		JdbcUtils.closeConnection(con);
//	}
}






