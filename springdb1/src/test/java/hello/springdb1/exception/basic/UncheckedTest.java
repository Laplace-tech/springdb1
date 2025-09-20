package hello.springdb1.exception.basic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UncheckedTest {

	@Test
	void unchecked_catch() {
		Service service = new Service();
		service.callCatch();
	}
	
	@Test
	void unchecked_throw() {
		Service service = new Service();
		assertThatThrownBy(() -> service.callThrow())
			.isInstanceOf(UncheckedException.class);
	}
	
	/**
	 * RuntimeException을 상속받은 예외는 언체크 예외가 된다.
	 */
	@SuppressWarnings("serial")
	static class UncheckedException extends RuntimeException {
		public UncheckedException(String message) {
			super(message);
		}
	}
	
	/**
	 * UnChecked 예외는 예외를 잡거나, 던지지 않아도 된다
	 * 예외를 잡지 않으면 자동으로 밖으로 던진다.
	 */
	static class Service {
		Repository repository = new Repository();
		
		public void callCatch() {
			try {
				repository.call();
			} catch (UncheckedException e) {
				log.info("예외 처리, message={}", e.getMessage(), e);
			}
		}
		
		/**
		 * 에외를 잡지 않아도 된다. 자연스럽게 상위로 넘어간다
		 * 체크 예외와 다르게 throws 예외 선언을 하지 않아도 된다.
		 */
		public void callThrow() {
			repository.call();
		}
	}
	
	static class Repository {
		public void call() {
			throw new UncheckedException("Exception");
		}
	}
	
}
