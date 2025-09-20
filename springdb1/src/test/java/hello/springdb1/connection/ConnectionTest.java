package hello.springdb1.connection;

import static hello.springdb1.Springdb1Application.*;
import static hello.springdb1.connection.ConnectionConst.PASSWORD;
import static hello.springdb1.connection.ConnectionConst.URL;
import static hello.springdb1.connection.ConnectionConst.USERNAME;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionTest {

	@Test
	void driverManager() throws SQLException {
		Connection con1 = DriverManager.getConnection(URL, USERNAME, PASSWORD);
		Connection con2 = DriverManager.getConnection(URL, USERNAME, PASSWORD);

		log.info("connection1: {}, class: {}", con1, con1.getClass());
		log.info("connection2: {}, class: {}", con2, con2.getClass());
	}

	@Test
	@DisplayName("DriverManagerDataSource : 항상 새로운 커넥션 휙득")
	void dataSourceDriverManager() throws SQLException {
		DataSource dataSource = createDriverManagerDataSource();
		for (int i = 0; i < 10; i++) {
			useDataSource(dataSource);
		}
	}

	@Test
	@DisplayName("HikariDataSource : 커넥션 풀 사용")
	void dataSourceConnectionPool() throws SQLException {
		HikariDataSource dataSource = createHikariDataSource();
		for (int i = 1; i <= dataSource.getMaximumPoolSize(); i++) {
			useDataSource(dataSource);
		}
		logPoolStatus(dataSource);
	}

}
