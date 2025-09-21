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
import hello.springdb1.repository.ex.MyDbException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 예외 누수(checked exception leak)
 * 
 * 1) 문제 정의: 체크 예외가 인터페이스를 오염시킴
 * - DAO/리포지토리가 DB 라이브러리(SQL 등)에서 발생하는 체크 예외(SQLException)를 그대로
 *   인터페이스에 선언하면(예: save(...) throws SQLException) 인터페이스 자체가 JDBC에 종속된다.
 * - 그 결과 서비스 계층은 특정 기술(예: JDBC)의 예외를 처리하거나 선언해야 하고,
 *   구현 기술을 바꾸면 인터페이스 + 서비스에 영향이 생긴다 (순수성 훼손)
 * 
 * 2) 기본 원칙
 * - 서비스 계층은 특정 기술(예: JDBC, JPA)에 의존하면 안 된다. 비즈니스 로직만 담아야 한다.
 * - 기술 특유의 체크 예외는 리포지토리에서 변환해서 밖으로 던져야 한다
 * 
 * 3) 해결책: 예외 변환(Exception Translation) - 체크 예외 -> 런타임 예외로 변환
 * - 리포지토리 구현체에서 SQLException 등을 잡아서 애플리케이션 전용 런타임 예외로 감싸서 던진다.
 *   예) catch (SQLException e) { throw new MyDbException(e); }
 * - 이때 꼭 '원인(cause)'으로 기존 예외를 전달해야 함 : new MyDbException(e) 
 * - 원인 예외를 포함하지 않으면 스택트레이스에서 근본 원인을 알 수 없어 디버깅이 힘듦.
 * 
 * 4) 인터페이스 설계 (순수 인터페이스)
 * - MemberRepository 인터페이스는 기술 의존 없이 다음처럼 선언
 * 		Member save(Member member)
 *  	Member findById(String memberId)
 *  	void update(String memberId, int money)
 *  	void delete(String memberId)
 *  
 * 5) 런타임 예외 클래스 설계(권장)
 *   - MyDbException extends RuntimeException
 *   - 생성자에 Throwable cause를 전달받는 생성자 제공 (super(cause))
 *   - 메시지와 원인 모두 전달할 수 있는 생성자 제공
 *   - (중요) 절대 원인 예외를 버리지 말 것! 반드시 포함.
 *  
 * 6) 트랜잭션과의 관계
 * - 스프링의 기본 @Transactional 동작:
 * 		- RuntimeException 또는 Error 발생 시 롤백
 * 		- 체크 예외(Exception) 발생 시 기본적으로 롤백하지 않음 -> "커밋"
 * - 따라서 "SQLException을 체크 예외 상태 그대로 둔다면" 
 * 		@Transactional이 "롤백을 자동으로 하지 않을 수 있다."
 * 
 * 7) 서비스 계층의 장접 (변환 적용 후)
 * - 서비스는 JDBC 예외에 신경 쓸 필요 없이 비즈니스 로직에만 집중
 * - 인터페이스 교체(예: JDBC -> JPA -> 외부 API) 시 서비스 코드 변경 불필요.
 * 
 * 8) 로깅 정책(권장 사항)
 *   - 예외를 변환해서 던질 때 이미 그 레이어에서 로깅하면, 상위 레이어까지 중복 로그가 남아 소음 발생.
 *   - 일반 권장: 예외를 변환(rethrow)할 때는 **원인 포함**만 하고, 
 *     실제 로깅은 컨트롤러 레벨이나 글로벌 예외처리(@ControllerAdvice)에서 일괄 처리.
 *   - 단, 리포지토리에서 중요한 내부 상태를 알리는 로그가 필요하면 debug/info로 남기고, 에러 로그는 상위에서 처리.
 *
 * 9) 예외 처리 흐름 요약 (실전)
 *   - RepositoryImpl (JDBC)
 *       try {
 *           // JDBC 작업
 *       } catch(SQLException e) {
 *           // 원인 포함해서 변환
 *           throw new MyDbException("DB 에러 발생", e);
 *       } finally {
 *           // 리소스 정리 (DataSourceUtils.releaseConnection 등)
 *       }
 *
 *   - Service (비즈니스)
 *       // throws 없음; @Transactional 사용 가능
 *       public void doBiz(...) {
 *           repository.save(...); // MyDbException 발생 가능 — 런타임
 *           ...
 *       }
 *
 *   - Controller / Global Handler
 *       @ControllerAdvice
 *       public class GlobalExceptionHandler {
 *           @ExceptionHandler(MyDbException.class)
 *           public ResponseEntity<ErrorDto> handleDb(MyDbException e) {
 *               // 로그 + 사용자용 메시지 반환 (500)
 *           }
 *       }
 */


/**
 * [개선 포인트 V3 → V4_1]
 * 
 * 1. 예외 처리 전략 변화
 * - V3에서는 SQLException 발생 시 RuntimeException으로 단순 변환
 * - V4_1에서는 SQLException → MyDbException (사용자 정의 예외)로 변환
 * - 서비스 계층은 JDBC 기술에 의존하지 않고, 오직 MyDbException 계층만 알면 됨
 * 
 * 2. 사용자 정의 예외 계층
 * - MyDbException : DB 관련 최상위 런타임 예외
 *    └─ MyDuplicateKeyException : 키 중복 전용 예외 (복구 가능)
 *      
 * 3. 장점
 * - 서비스 계층의 "순수성" 유지!! (JDBC, SQLException, Connection 몰라도 됨)
 * - 특정 오류코드(예: 23505 - 키 중복)를 잡아 복구 로직 처리 가능
 * - 기술 교체(JDBC -> JPA 등) 시에도 서비스 계층 코드 변경 최소화
 * 
 */
@Slf4j
@RequiredArgsConstructor
public class MemberRepositoryV4_1 implements MemberRepository {

	private final DataSource dataSource;

	@Override
	public void initTable() {
		String ddl = "create table if not exists member(" + 
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
			throw new MyDbException(e);
		} finally {
			close(stmt, con);
		}
	}

	@Override
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
			throw new MyDbException(e);
		} finally {
			close(stmt, con);
		}
	}

	@Override
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
			throw new MyDbException(e);
		} finally {
			close(pstmt, con);
		}
	}

	@Override
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
				Member findMember = new Member(rs.getString("member_id"), rs.getInt("money"));

				log.info("findById = {}", findMember);
				return findMember;
			} else {
				throw new NoSuchElementException("member not found member_id :" + memberId);
			}
		} catch (SQLException e) {
			log.error("DB Error - findById: {}", sql);
			throw new MyDbException(e);
		} finally {
			close(rs, pstmt, con);
		}
	}

	@Override
	public void update(String memberId, int money) {
		String sql = "update member set money = ? where member_id = ?";

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
			throw new MyDbException(e);
		} finally {
			close(pstmt, con);
		}
	}

	@Override
	public void delete(String memberId) {
		String sql = "delete from member where member_id = ?";

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
			throw new MyDbException(e);
		} finally {
			close(pstmt, con);
		}
	}

	@Override
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
			throw new MyDbException(e);
		} finally {
			close(pstmt, con);
		}
	}

	private void close(Statement stmt, Connection con) {
		close(null, stmt, con);
	}

	private void close(ResultSet rs, Statement stmt, Connection con) {
		JdbcUtils.closeResultSet(rs);
		JdbcUtils.closeStatement(stmt);
		DataSourceUtils.releaseConnection(con, dataSource);
	}

	private Connection getConnection() {
		Connection con = DataSourceUtils.getConnection(dataSource);
		log.info("get Connection : {}, class : {}", con, con.getClass());
		return con;
	}

}
