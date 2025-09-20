package hello.springdb1.exception.basic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CheckedAppTest {

	@Test
	void checked() {
		Controller controller = new Controller();
		assertThatThrownBy(() -> controller.request())
			.isInstanceOf(Exception.class);
	}
	
	static class Controller {
		Service service = new Service();
		
		public void request() throws ConnectException, SQLException {
			service.logic();
		}
	}
	
	static class Service {
		Repository repository = new Repository();
		NetworkClient networkClient = new NetworkClient();
		
		public void logic() throws SQLException, ConnectException {
			repository.call();
			networkClient.call();
		}
	}
	
	static class Repository {
		public void call() throws SQLException {
			throw new SQLException("SQL Exception");
		}
	}
	
	static class NetworkClient {
		public void call() throws ConnectException {
			throw new ConnectException("연결 실패");
		}
	}
}
