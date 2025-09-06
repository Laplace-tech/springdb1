package hello.springdb1.connection;

import static hello.springdb1.connection.ConnectionConst.PASSWORD;
import static hello.springdb1.connection.ConnectionConst.URL;
import static hello.springdb1.connection.ConnectionConst.USERNAME;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.zaxxer.hikari.HikariConfig;
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
	void dataSourceDriverManager() throws SQLException {
		// DriverManagerDataSource : 항상 새로운 커넥션 휙득
		DriverManagerDataSource dataSource = new DriverManagerDataSource(URL, USERNAME, PASSWORD);
		useDataSource(dataSource);
	}
	
	@Test
	void dataSourceConnectionPool() throws SQLException {
		try(HikariDataSource dataSource = createHikariDataSource()) {
            for (int i = 1; i <= 7; i++) {
                useDataSource(dataSource);
            }
            
            logPoolStatus(dataSource);
		}
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
	
	private void useDataSource(DataSource dataSource) throws SQLException {
		try (Connection con = dataSource.getConnection()) {
			log.info("connection={}, class={}", con, con.getClass());
		}
	}
	
	private void logPoolStatus(HikariDataSource dataSource) {
		log.info("HikariCP Pool Name={}, Active={}, Idle={}, await={}, total={}", 
				dataSource.getPoolName(),
				dataSource.getHikariPoolMXBean().getActiveConnections(),
				dataSource.getHikariPoolMXBean().getIdleConnections(),
				dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection(),
				dataSource.getHikariPoolMXBean().getTotalConnections());
	}
}
