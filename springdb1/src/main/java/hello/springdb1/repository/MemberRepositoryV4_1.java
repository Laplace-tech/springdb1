package hello.springdb1.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.NoSuchElementException;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Repository;

import hello.springdb1.domain.Member;
import hello.springdb1.repository.ex.MyDbException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MemberRepositoryV4_1 implements MemberRepository {

	private final DataSource dataSource;
	
    // ========== 테이블 관리 ================
	@Override
    public void initTable() {
        String ddl = "create table if not exists member (" +
                     "member_id varchar(10) primary key, " +
                     "money integer not null default 0)";
		log.info("Table member creating...");
        execute(ddl);
    }

	@Override
    public void dropTable() {
        String ddl = "drop table if exists member";
		log.info("Table member dropping...");
        execute(ddl);
    }
	
	@Override
	public Member save(Member member) {
		String sql = "insert into member(member_id, money) values (?, ?)";
		log.info("저장 실행: memberId={}, money={}", member.getMemberId(), member.getMoney());

		Connection con = null;
		PreparedStatement pstmt = null;

		try {
			con = getConnection();
			pstmt = con.prepareStatement(sql);

			pstmt.setString(1, member.getMemberId());
			pstmt.setInt(2, member.getMoney());
			pstmt.executeUpdate();

			return member;
		} catch (SQLException e) {
			log.error("DB 오류 - save(memberId={})", member.getMemberId(), e);
			throw new MyDbException(e);
		} finally {
			close(pstmt, con);
		}
	}

	@Override
	public Member findById(String memberId) {
		String sql = "select * from member where member_id = ?";
		log.info("단건 조회 실행: memberId={}", memberId);

		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;

		try {
			con = getConnection();
			pstmt = con.prepareStatement(sql);

			pstmt.setString(1, memberId);

			rs = pstmt.executeQuery();
			if (rs.next()) {
				return new Member(rs.getString("member_id"), rs.getInt("money"));
			} else {
				throw new NoSuchElementException("member not found memberId = " + memberId);
			}
		} catch (SQLException e) {
			log.error("DB 오류 - findById(memberId={})", memberId, e);
			throw new MyDbException(e);
		} finally {
			close(rs, pstmt, con);
		}
	}

	@Override
	public Member update(String memberId, int money) {
		String sql = "update member set money=? where member_id = ?";
		log.info("수정 실행: memberId={}, money={}", memberId, money);

		Connection con = null;
		PreparedStatement pstmt = null;
		try {
			con = getConnection();
			pstmt = con.prepareStatement(sql);

			pstmt.setInt(1, money);
			pstmt.setString(2, memberId);

			int resultSize = pstmt.executeUpdate();
			log.info("수정된 row 수: {}", resultSize);

			return findById(memberId);
		} catch (SQLException e) {
			log.error("DB 오류 - update(memberId={}, money={})", memberId, money);
			throw new MyDbException(e);
		} finally {
			close(pstmt, con);
		}
	}

	@Override
	public void delete(String memberId) {
		String sql = "delete from member where member_id=?";
		log.info("삭제 실행: memberId={}", memberId);

		Connection con = null;
		PreparedStatement pstmt = null;
		try {
			con = getConnection();
			pstmt = con.prepareStatement(sql);
			
			pstmt.setString(1, memberId);
			
			pstmt.executeUpdate(sql);
		} catch (SQLException e) {
			log.error("DB 오류 - delete(memberId={})", memberId);
			throw new MyDbException(e);
		} finally {
			close(pstmt, con);
		}
	}

	public void deleteAll() {
		String sql = "delete from member";
		log.info("전체 삭제 실행");
		
		Connection con = null;
		Statement stmt = null;
		try {
			con = getConnection();
			stmt = con.createStatement();
			
			stmt.executeUpdate(sql);
		} catch (SQLException e) {
			log.error("DB 오류 - deleteAll");
			throw new MyDbException(e);
		} finally {
			close(stmt, con);
		}
	}
	
	private void execute(String ddl, Objects... params) {
		Connection con = null;
		PreparedStatement pstmt = null;
		
		try {
			con = getConnection();
			pstmt = con.prepareStatement(ddl);
			
			for(int i = 0; i < params.length; i++) {
				pstmt.setObject(i+1, params[i]);
			}
			int resultSize = pstmt.executeUpdate();
			log.info("쿼리 실행 완료: sql={}, params={}, 영향 받은 row={}", ddl, params, resultSize);
        } catch (SQLException e) {
            log.error("DB Error - SQL: {}", ddl, e);
            throw new MyDbException(e);
        } finally {
        	close(pstmt, con);
		}
	}
	
	private Connection getConnection() {
		return DataSourceUtils.getConnection(dataSource);
	}
	
	private void close(Statement stmt, Connection con) {
		close(null, stmt, con);
	}
	
	private void close(ResultSet rs, Statement stmt, Connection con) {
		JdbcUtils.closeResultSet(rs);
		JdbcUtils.closeStatement(stmt);
		DataSourceUtils.releaseConnection(con, dataSource);
	}
}
