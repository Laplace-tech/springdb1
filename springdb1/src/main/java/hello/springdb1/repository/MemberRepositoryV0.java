package hello.springdb1.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.NoSuchElementException;

import hello.springdb1.connection.DBConnectionUtil;
import hello.springdb1.domain.Member;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemberRepositoryV0 {

	/**
	 * 테이블 생성
	 */
	public void initTable() {
		String ddl = "create table if not exists member (" 
					+ "member_id varchar(10) primary key, "
					+ "money integer not null default 0)";

		try (Connection con = DBConnectionUtil.getConnection(); 
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

		try (Connection con = DBConnectionUtil.getConnection(); 
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
	
	try(Connection con = DBConnectionUtil.getConnection();
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

		try (Connection con = DBConnectionUtil.getConnection(); 
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

		try (Connection con = DBConnectionUtil.getConnection(); 
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
		
		try (Connection con = DBConnectionUtil.getConnection(); 
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
		
		try (Connection con = DBConnectionUtil.getConnection(); 
			 PreparedStatement pstmt = con.prepareStatement(sql)) {
			
			int resultSize = pstmt.executeUpdate();
			log.info("deleteAll resultSize={}", resultSize);
		} catch (SQLException e) {
			log.error("DB Error", e);
			throw new RuntimeException(e);
		}
	}

}
