package hello.springdb1;

import static hello.springdb1.connection.ConnectionConst.PASSWORD;
import static hello.springdb1.connection.ConnectionConst.URL;
import static hello.springdb1.connection.ConnectionConst.USERNAME;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import hello.springdb1.repository.MemberRepositoryV1;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Springdb1ApplicationTests {

	private final DataSource hikariDataSource = createHikariDataSource();
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
	void autoCommitVsManualCommit() throws SQLException {
		String sql = "insert into member(member_id, money) values (?, ?)";
		Connection con = hikariDataSource.getConnection();
		
		// 기본은 autoCommit = true
		log.info("autoCommit={}", con.getAutoCommit());
		
		// 1) 자동 커밋 모드
		try(PreparedStatement pstmt = con.prepareStatement(sql)) {
			pstmt.setString(1, "data1");
			pstmt.setInt(2, 10000);
			int resultSize = pstmt.executeUpdate(); // 바로 커밋됨
			log.info("resultSize = {}", resultSize);
		}
		
		con.setAutoCommit(false);
		log.info("autoCommit={}", con.getAutoCommit());
		// 2) 수동 커밋 모드
		try(PreparedStatement pstmt = con.prepareStatement(sql)) {
			pstmt.setString(1, "data2");
			pstmt.setInt(2, 20000);
			int resultSize = pstmt.executeUpdate(); // 바로 커밋됨
			log.info("resultSize = {}", resultSize);
		}
		
		// rollBack 테스트
		con.rollback(); // data2 삽입 취소
		
		con.setAutoCommit(true);
		con.close();
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
	
}

