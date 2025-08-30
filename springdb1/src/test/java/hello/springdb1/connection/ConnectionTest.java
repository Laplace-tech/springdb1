package hello.springdb1.connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import lombok.extern.slf4j.Slf4j;
import static hello.springdb1.connection.ConnectionConst.*;

@Slf4j
public class ConnectionTest {

	@Test
	void driverManager() throws SQLException {
		Connection con1 = DriverManager.getConnection(URL, USERNAME, PASSWORD);
		Connection con2 = DriverManager.getConnection(URL, USERNAME, PASSWORD);
		
		log.info("connection1: {}, class: {}", con1, con1.getClass());
		log.info("connection2: {}, class: {}", con2, con2.getClass());
	}
	
	
}
