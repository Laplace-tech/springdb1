package hello.springdb1.exception.basic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CheckedTest {

	@Test
	void checked_catch() {
		Service service = new Service();
		service.callCatch();
	}
	
	@Test
	void checked_throw() {
		Service service = new Service();
		assertThatThrownBy(() -> service.callThrow())
			.isInstanceOf(CheckedException.class);
	}
	
	/**
	 * Exception 을 상속받은 예외는 체크 예외가 된다.
	 */
	@SuppressWarnings("serial")
	static class CheckedException extends Exception {
		public CheckedException(String message) {
			super(message);
		}
	}
	
	static class Service {
		Repository repository = new Repository();
		
		/**
		 * 예외를 잡아서 처리하는 코드
		 */
		public void callCatch() {
			try {
				repository.call();
			} catch (CheckedException e) {
				log.info("예외 처리, message = {}", e.getMessage(), e);
			}
		}
		
		/**
		 * 체크 예외를 밖으로 던지는 코드
		 * 체크 예외는 예외를 잡지 않고 밖으로 던지려면 throws 를 메서드에 필수로 선언해야 한다.
		 * @throws CheckedException
		 */
		public void callThrow() throws CheckedException {
			repository.call();
		}
	}
	
	static class Repository {
		public void call() throws CheckedException{
			throw new CheckedException("ex");
		}
	}
}
