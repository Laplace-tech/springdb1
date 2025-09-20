package hello.springdb1.exception.basic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UncheckedAppTest {

    @Test
    void unchecked() {
        Controller controller = new Controller();
        assertThatThrownBy(() -> controller.request())
          .isInstanceOf(Exception.class);
    }
	
	@Test
	void printException() {
		Controller controller = new Controller();
		try {
			controller.request();
		} catch (Exception e) {
			log.info("Exception", e);
		}
	}
	
	static class Controller {
		Service service = new Service();
		
		public void request() {
			service.logic();
		}
	}
	
	static class Service {
		Repository repository = new Repository();
		NetworkClient networkClient = new NetworkClient();
		
		public void logic() {
			repository.call();
			networkClient.call();
		}
	}
	
	static class NetworkClient {
		public void call() {	
			throw new RuntimeConnectException("연결 실패");
		}
	}
	
	static class Repository {
		public void call() {
			try {
				runSQL();
			} catch (SQLException e) {
				throw new RuntimeSQLException(e);
			}
		}
		
		private void runSQL() throws SQLException {
			throw new SQLException("SQL Exception");
		}
	}
	
    @SuppressWarnings("serial")
	static class RuntimeConnectException extends RuntimeException {
        public RuntimeConnectException(String message) {
            super(message);
        }
    }
	
	@SuppressWarnings("serial")
	static class RuntimeSQLException extends RuntimeException {
		public RuntimeSQLException() {}
		public RuntimeSQLException(Throwable cause) {
			super(cause);
		}
	}
	
}
