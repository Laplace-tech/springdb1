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

/**
 * MemberRepositoryV0
 * 
 * 개요
 * - 순수 JDBC만으로 Member 엔티티의 CRUD를 수행하는 리포지토리 구현체
 * - JDBC의 기초(커넥션 휙득, PreparedStatement 사용, ResultSet 처리,
 * 	 try-with-resources로 자원 정리)를 직접 보여줌
 * 
 * 핵심 기능
 * - DB와의 직접 통신을 담당(데이터 접근 계층)
 * - SQL을 실행하고, ResultSet을 도메일 객체(Member)로 변환
 * - DB 특유의 예외(SQLException 등)를 캡슐화(여기서는 RuntimeException으로 래핑)
 * - 상위 계층(Service)는 도메인 객체와 비즈니스 로직에만 집중하도록 지원
 * 
 * 상세 설명
 * 1) 리소스 관리
 *  - Connection, Statement(PreparedStatement), ResultSet은 반드시 close 해야 한다.
 *  - try-with-resources 를 통해 자동으로 닫도록 작성되어 있어 안전함
 *  
 * 2) 예외처리 전략
 *  - 현재: SQLException을 catch 한 뒤 RuntimeException으로 감싸서 던짐,
 *    그래서 서비스 계층이 checked exception 때문에 오염되지 않음.
 *    그러나, 구체적인 예외 식별을 위해서는 예외 변환 계층이 더 필요.
 *  - 권장 : MyDbException 등 앱 전용 런타임 예외로 변환. (cause 를 반드시 포함 시켜야 함)
 *  
 * 3) 트랜잭션 참여 여부
 *  - 현재 방식은 각 메서드가 자체적으로 커넥션을 열어 작업 후 닫음.
 *    그러나, 여러 메서드를 하나의 트랜잭션으로 묶고 싶으면 별도 트랜잭션 관리
 *    (외부에서 같은 커넥션을 전달하거나 Spring 트랜잭션 관리 + DataSourceUtils)가 필요
 *  
 * 4) 동시성·성능 고려
 *  - DBConnectionUtil.getConnection() 구현이 DriverManager 기반이면 연결 생성 비용이 큼.
 *  - 실제 운영에서는 커넥션 풀(HikariCP)을 사용해야 함.
 *  
 */

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

		try (Connection con = DBConnectionUtil.getConnection();
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
