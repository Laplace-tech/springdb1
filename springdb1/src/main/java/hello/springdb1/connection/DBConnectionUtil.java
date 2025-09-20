package hello.springdb1.connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 1. JDBC 연결 및 DriverManager.getConnection()
 * 
 * - JDBC (Java Database Connectivity): Java 에서 데이터베이스와의 연결을 위해 제공하는 API.
 *   이 API는 java.sql.Connection, 
 *           java.sql.Statement, 
 *           java.sql.ResultSet 등의 클래스를 포함.
 *          
 * - DriverManager: JDBC에서 데이터베이스 드라이버를 로드하고, 
 *    각 드라이버가 제공하는 연결(커넥션)을 관리하는 역할을 한다.
 *    
 *   DriverManager.getConnection()을 호출하면 
 *    해당 URL을 처리할 수 있는 드라이버를 찾아서 데이터베이스 연결을 제공한다.
 * ---------------------------------------------------------------------------------------------
 * 2. DriverManager.getConnection()의 동작 흐름
 * 
 * - DriverManager.getConnection() 호출 시, DriverManager는 라이브러리에 등록된 모든 
 *   JDBC 드라이버들을 확인하고, 주어진 URL을 처리할 수 있는 드라이버를 찾아 연결을 만든다.
 *   
 *   1. 애플리케이션에서 getConnection() 호출
 *     애플리케이션 코드에서 DriverManager.getConnection()을 호출하면,
 *     JDBC 연결을 생성하려는 요청이 시작된다.
 *     
 *   2. 드라이버 목록 확인
 *     DriverManager는 등록된 드라이버들을 확인하고, 드라이버가 해당 URL을 처리할 수 있는지 검사한다
 *     예를 들어, URL이 jdbc:h2://localhost/~/test 라면, H2 드라이버가 이 URL을 처리할 수 있음을 인식한다.
 *   
 *   3. 드라이버가 처리할 수 있으면 연결 생성
 *     만약 URL이 jdbc:h2://로 시작하고, H2 드라이버가 등록되어 있으면, 
 *     H2 드라이버는 실제 데이터베이스와 연결을 시도하여 Connection 객체를 반환한다.
 *     
 *   4. 연결 반환
 *     드라이버가 성공적으로 연결을 처리하면, Connection 객체가 애플리케이션에 반환된다.
 *     이 객체는 "java.sql.Connection 인터페이스를 구현한, 실제 데이터베이스와 연결된 객체다"
 *   
 *   5. 결과
 *     이 Connection 객체를 통해 애플리케이션은 데이터베이스와 상호작용할 수 있다.
 * ----------------------------------------------------------------------------------------------
 * 3. H2 드라이버와 연결 흐름
 * 
 * - H2 데이터베이스의 경우, DriverManager.getConnection() 호출 시, 
 *   H2 드라이버가 jdbc:h2 URL을 처리하게 된다. 연결을 성공적으로 설정하면
 *   "org.h2.jdbc.JdbcConnection" 객체를 반환하는데, 이는 "H2가 구현한 Connection 객체"이다.
 *   이 객체는 java.sql.Connection 인터페이스를 구현했으므로 JDBC 표준을 따른다.
 * -----------------------------------------------------------------------------------------
 * 4. H2 데이터베이스 커넥션 객체
 * 
 * - 실행 로그에서 확인할 수 있는 "class=class org.h2.jdbc.JdbcConnection" 부분은 
 *   "H2 드라이버가 반환한 Connection 객체의 클래스" 이름이다. 
 *   이 객체는 "java.sql.Connection 인터페이스를 구현한 H2 전용 연결 객체"이다. 
 *   Connection 인터페이스는 JDBC 표준에 맞게 설계되었기 때문에, 애플리케이션에서는
 *   이 객체를 Connection 인터페이스를 통해 다루게 된다.
 * 
 */

// DB 연결을 관리하는 유틸리티 클래스 - 데이터베이스에 연결하는 기능을 재사용 가능하게 제공하는 것
public class DBConnectionUtil {

	public static Connection getConnection() throws SQLException {

		try {
			// JDBC 드라이버를 이용해서 연결을 가져온다.
			Connection con = DriverManager.getConnection(
					ConnectionConst.URL, 
					ConnectionConst.USERNAME, 
					ConnectionConst.PASSWORD);
			
			System.out.println("DB 연결 성공 : " + con); // 연결 성공 시 출력
			return con;
		} catch (SQLException e) {
			// 예외가 발생하면 에러 메세지를 출력하고 다시 예외를 던진다
			System.err.println("DB 연결 오류: " + e.getMessage());
			throw e; // SQLException을 그대로 던지기
		}
	}

}
