package hello.springdb1.repository.ex;

public class MyDuplicateKeyException extends MyDbException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public MyDuplicateKeyException() {}

	public MyDuplicateKeyException(String message) {
		super(message);
	}
	
	public MyDuplicateKeyException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public MyDuplicateKeyException(Throwable cause) {
		super(cause);
	}
	
}
