package hello.springdb1.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DBConnectionUtilTest {

	@Test
	void connection() throws SQLException {
		Connection connection = DBConnectionUtil.getConnection();
		assertThat(connection).isNotNull();
	}
	
}
