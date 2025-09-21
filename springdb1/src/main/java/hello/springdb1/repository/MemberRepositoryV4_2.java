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
import org.springframework.jdbc.support.SQLExceptionTranslator;

import hello.springdb1.domain.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [MemberRepositoryV4_2 - 스프링 예외 추상화 적용]
 * 
 * 1. 목적
 * - 기존 JDBC SQLException 직접 처리 대신
 *   스프링이 제공하는 SQLExceptionTranslator 사용
 * - 서비스 계층에서 특정 DB 구현 기술과 예외에 종속되지 않도록 설계
 * 
 * 2. 구성
 * - DataSource 주입
 * - SQLErrorCodeSQLExceptionTranslator 생성
 *   -> SQL ErrorCode에 맞춰서 스프링 데이터 접근 예외로 변환한다.
 *   
 * 3. save, findById, update, delete 메서드
 * - try-catch 에서 SQLException 발생 시, exTranslator.translate("operation", sql, e) 호출
 * - 반환되는 예외: Spring 데이터 접근 예외(DataAccessException 하위) - 특정 기술에 종속X 
 * 
 * 4. 커넥션 관리
 * - getConnection() : DataSourceUtils.getConnection 사용
 *  -> 트랜잭션과 동기화된 Connection 확보
 * - close() : JdbcUtils + DataSourceUtils.releaseConnection 
 *  -> ResultSet, Statement, Connection 안전하게 정리 
 *  
 * 5. 정리
 * - 서비스 계층: 특정 구현 기술(JDBC, H2 등)과 예외에 종속되지 않음
 * - 구현 기술 적용(JDBC -> JPA) 시 서비스 코드 변경 최소화
 * - 필요 시 서비스 계층에서 스프링 예외를 잡아 복구 가능
 * 
 */

/**
 * [V4_1 -> V4_2 개선 포인트]
 * 
 * [스프링 예외 변환 - SQLExceptionTranslator]
 * 
 * - SQLExceptionTranslator 도입
 *  -> DataAccessException 기반 스프링 예외로 변환
 *  -> DB 종류에 따라 적절한 예외를 추상화해서 던짐
 *  -> 예: DuplicateKeyException, DataIntegrityViolationException 등
 * 
 * - 코드 구조는 거의 동일하지만
 *  -> catch(SQLException e) { throw exTranslator.translate(...) } 패턴 사용
 * 
 */
@Slf4j
@RequiredArgsConstructor
public class MemberRepositoryV4_2 implements MemberRepository {

	private final DataSource dataSource;
	private final SQLExceptionTranslator exTranslator;
	
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
			throw exTranslator.translate("create", ddl, e);
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
			throw exTranslator.translate("drop", ddl, e);
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
			throw exTranslator.translate("save", sql, e);
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
			throw exTranslator.translate("select", sql, e);
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
			throw exTranslator.translate("update", sql, e);
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
			throw exTranslator.translate("delete", sql, e);
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
			throw exTranslator.translate("delete", sql, e);
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
