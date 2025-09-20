package hello.springdb1.exception.translator;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;

import hello.springdb1.Springdb1Application;
import lombok.extern.slf4j.Slf4j;

/**
 * [스프링 예외 추상화]
 *
 * - 스프링은 데이터 접근과 관련된 예외를 추상화해서 제공
 * - 데이터 접근 계층에 대한 수십 가지 예외를 정리, 일관된 예외 계층 제공
 * - 각각의 예외는 특정 기술에 종속적이지 않게 설계됨
 * - 서비스 계층에서는 스프링이 제공하는 예외를 사용하면 됨
 *   (JDBC, JPA 상관없이 스프링 예외 사용)
 * - JDBC나 JPA 예외를 스프링이 제공하는 예외로 변환하는 기능 제공
 *
 * [최상위 예외]
 * - org.springframework.dao.DataAccessException
 * - 런타임 예외 상속 → 모든 데이터 접근 예외는 런타임 예외
 *
 * [예외 구분]
 * - Transient (일시적 오류 → 재시도 가능)
 *   ex) 쿼리 타임아웃, 락 관련 오류
 * - NonTransient (영구적 오류 → 재시도 불가)
 *   ex) SQL 문법 오류, 제약조건 위배
 *
 * [예외 변환기]
 * - 스프링은 SQLException의 ErrorCode를 스프링 예외로 변환
 * - SQLExceptionTranslator 사용
 *
 * 예시:
 * SQLExceptionTranslator exTranslator = new SQLErrorCodeSQLExceptionTranslator(dataSource);
 * DataAccessException resultEx = exTranslator.translate("select", sql, e);
 *
 * - translate() 파라미터
 *   1) 설명용 문자열
 *   2) 실행한 SQL
 *   3) 발생한 SQLException
 * - 반환: 스프링이 정의한 적절한 예외 (ex. BadSqlGrammarException)
 *
 * [sql-error-codes.xml]
 * - DB별 ErrorCode → 스프링 예외 매핑 정보 제공
 * - H2 예시:
 *     badSqlGrammarCodes: 42000,42001,42101,42102,42111,42112,42121,42122,42132
 *     duplicateKeyCodes: 23001,23505
 * - MySQL 예시:
 *     badSqlGrammarCodes: 1054,1064,1146
 *     duplicateKeyCodes: 1062
 *
 * [정리]
 * - 스프링은 데이터 접근 계층에 대한 일관된 예외 추상화 제공
 * - 예외 변환기를 통해 SQLException의 ErrorCode를 스프링 예외로 변환
 * - 서비스, 컨트롤러 계층에서는 SQLException 같은 기술 종속 예외 대신
 *   스프링이 제공하는 데이터 접근 예외를 사용
 * - JDBC에서 JPA로 기술을 변경해도 예외 처리 변경 최소화
 * - 단, 스프링에 대한 기술 종속성은 발생
 * - 완전한 독립을 원하면 예외를 직접 정의하고 변환해야 하지만 실용적이지 않음
 */


@Slf4j
public class SpringExceptionTranslatorTest {

	private final DataSource dataSource = Springdb1Application.createHikariDataSource();
	
	@Test
	void exceptionTranslator() {
		String sql = "select bad grammer";
		
		Connection con = null;
		Statement stmt = null;
		try {
			con = DataSourceUtils.getConnection(dataSource);
			stmt = con.prepareStatement(sql);
			stmt.executeQuery(sql);
		} catch (SQLException e) {
			assertThat(e.getErrorCode()).isEqualTo(42122);
	        //org.springframework.jdbc.support.sql-error-codes.xml
			
			SQLExceptionTranslator exTranslator = new SQLErrorCodeSQLExceptionTranslator(dataSource);
	        //org.springframework.jdbc.BadSqlGrammarException
			DataAccessException resultEx = exTranslator.translate("select", sql, e);
			log.info("resultEx", resultEx);
	        assertThat(resultEx.getClass()).isEqualTo(BadSqlGrammarException.class);
		} finally {
			JdbcUtils.closeStatement(stmt);
			DataSourceUtils.releaseConnection(con, dataSource);
		}
	}
	
}
