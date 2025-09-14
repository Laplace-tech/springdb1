package hello.springdb1.repository.ex;

@SuppressWarnings("serial")
public class MyDbException extends RuntimeException {

	public MyDbException() {
		
	}
	
	public MyDbException(String message) {
		super(message);
	}
	
	public MyDbException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public MyDbException(Throwable cause) {
		super(cause);
	}
}
