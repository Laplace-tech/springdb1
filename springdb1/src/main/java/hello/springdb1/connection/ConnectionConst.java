package hello.springdb1.connection;


/**
 * 이 클래스는 DB 연결 관련 상수를 관리하는 곳
 * DB URL, 사용자 이름, 비밀번호를 여기서 정의해 놓고 코드 다른 곳에서 사용할 수 있다
 */
public abstract class ConnectionConst {

	/**
	 * - ~/test는 사용자의 홈 디렉토리에 test.mv.db 파일이 위치한다
	 * - H2의 기본 사용자 명은 'sa'
	 * - 기본 비밀번호는 공백으로 설정되어 있다
	 */
	public static final String URL = "jdbc:h2:~/test";
	public static final String USERNAME = "sa";
	public static final String PASSWORD = "";
	
}
